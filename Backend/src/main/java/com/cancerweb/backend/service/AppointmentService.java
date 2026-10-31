package com.cancerweb.backend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.AppointmentRequest;
import com.cancerweb.backend.dto.AppointmentResponse;
import com.cancerweb.backend.dto.RescheduleRequest;
import com.cancerweb.backend.dto.SlotDay;
import com.cancerweb.backend.exception.AppointmentNotFoundException;
import com.cancerweb.backend.exception.BookingConflictException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.model.Appointment;
import com.cancerweb.backend.model.AppointmentStatus;
import com.cancerweb.backend.model.BreastSurgeonProfile;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.AppointmentRepository;
import com.cancerweb.backend.repository.BreastSurgeonProfileRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.CurrentUser;

/**
 * Booking, moving and closing Breast Surgeon appointments. Every rule is checked here, whatever the page sent:
 * the surgeon must be an approved Breast Surgeon taking bookings, the slot must be one they offer, in the
 * future and within {@value #BOOKING_WINDOW_DAYS} days, and nobody else may hold it. That last check is the
 * unique index on {@code slotKey}, so two patients booking the same slot at the same moment cannot both win.
 *
 * <p>Patients act only on their own appointments and surgeons only on theirs; anything else is "not found".
 * Dates and times are in the clinic time zone (app.appointments.time-zone).
 */
@Service
public class AppointmentService {

    static final int BOOKING_WINDOW_DAYS = 60;
    static final int DEFAULT_CALENDAR_DAYS = 14;

    private static final List<AppointmentStatus> ACTIVE = List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppointmentRepository appointments;
    private final BreastSurgeonProfileRepository profiles;
    private final BreastSurgeonService surgeons;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final NotificationService notifications;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, BreastSurgeonProfileRepository profiles,
            BreastSurgeonService surgeons, UserRepository users, MongoTemplate mongo,
            @Value("${app.appointments.time-zone:Asia/Dhaka}") String timeZone, NotificationService notifications) {
        this.appointments = appointments;
        this.profiles = profiles;
        this.surgeons = surgeons;
        this.users = users;
        this.mongo = mongo;
        this.notifications = notifications;
        this.clock = Clock.system(ZoneId.of(timeZone));
    }

    // ----- Calendar -----

    /** The surgeon's offered slots for the next {@code days} days (1 to 60, from today), marked free or not. */
    public List<SlotDay> calendar(String surgeonId, Integer days) {
        BreastSurgeonProfile profile = surgeons.getApproved(surgeonId);
        int count = days == null ? DEFAULT_CALENDAR_DAYS : Math.max(1, Math.min(BOOKING_WINDOW_DAYS, days));
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalDate last = today.plusDays(count - 1L);
        Set<String> taken = takenSlots(profile.id(), today, last);
        Set<String> offeredDays = Set.copyOf(profile.availableDays());
        List<SlotDay> calendar = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(last); date = date.plusDays(1)) {
            if (!offeredDays.contains(date.getDayOfWeek().name())) {
                continue;
            }
            String day = date.toString();
            boolean isToday = date.equals(today);
            List<SlotDay.Slot> slots = profile.timeSlots().stream()
                    .map(time -> new SlotDay.Slot(time, profile.isAcceptingAppointments()
                            && !taken.contains(Appointment.slotKey(profile.id(), day, time))
                            && !(isToday && !LocalTime.parse(time, HH_MM).isAfter(now))))
                    .toList();
            calendar.add(new SlotDay(day, date.getDayOfWeek().name(), slots));
        }
        return calendar;
    }

    private Set<String> takenSlots(String surgeonId, LocalDate from, LocalDate to) {
        Query query = Query.query(Criteria.where("surgeonId").is(surgeonId)
                .and("date").gte(from.toString()).lte(to.toString())
                .and("slotKey").exists(true));
        query.fields().include("slotKey");
        return mongo.find(query, Appointment.class).stream().map(Appointment::slotKey).collect(Collectors.toSet());
    }

    // ----- Patients -----

    /** Books a slot for the signed-in patient. The new appointment is PENDING until the surgeon confirms it. */
    public Appointment book(CurrentUser patient, AppointmentRequest request) {
        BreastSurgeonProfile profile = surgeons.requireBookable(request.surgeonId());
        requireBookableSlot(profile, request.date(), request.time());
        boolean alreadyBooked = mongo.exists(Query.query(Criteria.where("patientId").is(patient.id())
                .and("surgeonId").is(profile.id()).and("status").in(ACTIVE)), Appointment.class);
        if (alreadyBooked) {
            throw new BookingConflictException(
                    "You already have an upcoming appointment with this surgeon. Reschedule that one instead.");
        }
        User account = users.findById(patient.id()).orElseThrow(AppointmentNotFoundException::new);
        Instant now = Instant.now();
        // A clash on the readable code is very unlikely; on the slot it means someone else just took it.
        for (int attempt = 0; ; attempt++) {
            Appointment appointment = new Appointment(null, newCode(), patient.id(), account.fullName(), account.email(),
                    account.profile().phone(), profile.id(), profile.userId(), profile.fullName(), profile.hospitalName(),
                    profile.consultationFee(), request.date(), request.time(), request.reason(), AppointmentStatus.PENDING,
                    Appointment.slotKey(profile.id(), request.date(), request.time()), now, now, null, null, null, null,
                    null, null, null, 0);
            try {
                Appointment saved = appointments.insert(appointment);
                notifications.notify(saved.surgeonUserId(), "APPOINTMENT_REQUESTED", "New appointment request",
                        saved.patientName() + " asked for " + when(saved) + ".", "/surgeon-dashboard");
                return saved;
            } catch (DuplicateKeyException e) {
                if (isSlotClash(e) || attempt >= 2) {
                    throw slotTaken();
                }
            }
        }
    }

    public List<Appointment> listForPatient(String patientId) {
        return appointments.findAllByPatientIdOrderByDateDescTimeDesc(patientId);
    }

    /** Cancels the patient's own PENDING or CONFIRMED appointment and frees its slot. */
    public Appointment cancelByPatient(String patientId, String id, String reason) {
        return cancel("patientId", patientId, id, reason, "PATIENT");
    }

    /**
     * Moves the patient's own PENDING or CONFIRMED appointment to another free slot with the same surgeon. It
     * goes back to PENDING for the surgeon to confirm the new time. The old slot is freed in the same update.
     */
    public Appointment reschedule(String patientId, String id, RescheduleRequest request) {
        Appointment current = appointments.findByIdAndPatientId(id, patientId).orElseThrow(AppointmentNotFoundException::new);
        if (!ACTIVE.contains(current.status())) {
            throw new BookingConflictException("A " + current.status().name().toLowerCase() + " appointment cannot be rescheduled.");
        }
        if (current.date().equals(request.date()) && current.time().equals(request.time())) {
            throw new FieldValidationException("time", "Choose a different date or time");
        }
        BreastSurgeonProfile profile = surgeons.requireBookable(current.surgeonId());
        requireBookableSlot(profile, request.date(), request.time());
        // Matches only the version that was checked above, so a change in between is not overwritten.
        Query query = Query.query(Criteria.where("id").is(id).and("patientId").is(patientId)
                .and("status").in(ACTIVE).and("date").is(current.date()).and("time").is(current.time()));
        Update update = new Update()
                .set("date", request.date())
                .set("time", request.time())
                .set("slotKey", Appointment.slotKey(current.surgeonId(), request.date(), request.time()))
                .set("status", AppointmentStatus.PENDING)
                .set("previousDate", current.date())
                .set("previousTime", current.time())
                .inc("rescheduleCount", 1)
                .unset("confirmedAt")
                .set("updatedAt", Instant.now());
        Appointment updated;
        try {
            updated = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Appointment.class);
        } catch (DuplicateKeyException e) {
            throw slotTaken();
        }
        if (updated == null) {
            throw new BookingConflictException("This appointment changed in the meantime. Refresh and try again.");
        }
        notifications.notify(updated.surgeonUserId(), "APPOINTMENT_RESCHEDULED", "Appointment rescheduled",
                updated.patientName() + " moved their appointment to " + when(updated) + ". Please confirm the new time.", "/surgeon-dashboard");
        return updated;
    }

    // ----- Surgeons -----

    /** The signed-in surgeon's appointments, soonest first. */
    public List<Appointment> listForSurgeon(String surgeonUserId) {
        return appointments.findAllBySurgeonUserIdOrderByDateAscTimeAsc(surgeonUserId);
    }

    public Appointment confirm(String surgeonUserId, String id) {
        Appointment confirmed = transition(surgeonUserId, id, List.of(AppointmentStatus.PENDING),
                new Update().set("status", AppointmentStatus.CONFIRMED).set("confirmedAt", Instant.now()),
                "Only pending requests can be confirmed.");
        notifications.notify(confirmed.patientId(), "APPOINTMENT_CONFIRMED", "Appointment confirmed",
                confirmed.surgeonName() + " confirmed your appointment on " + when(confirmed) + ".", "/my-appointments");
        return confirmed;
    }

    /** Rejects a pending request or cancels a confirmed appointment; either way the slot is freed. */
    public Appointment cancelBySurgeon(String surgeonUserId, String id, String reason) {
        return cancel("surgeonUserId", surgeonUserId, id, reason, "SURGEON");
    }

    /** Marks a confirmed appointment as completed, on or after its day. */
    public Appointment complete(String surgeonUserId, String id) {
        Appointment current = appointments.findByIdAndSurgeonUserId(id, surgeonUserId).orElseThrow(AppointmentNotFoundException::new);
        if (current.status() == AppointmentStatus.CONFIRMED && LocalDate.parse(current.date()).isAfter(LocalDate.now(clock))) {
            throw new BookingConflictException("You can mark it completed on or after the appointment day.");
        }
        Appointment completed = transition(surgeonUserId, id, List.of(AppointmentStatus.CONFIRMED),
                new Update().set("status", AppointmentStatus.COMPLETED).set("completedAt", Instant.now()),
                "Only confirmed appointments can be marked completed.");
        notifications.notify(completed.patientId(), "APPOINTMENT_COMPLETED", "Appointment completed",
                "Your consultation with " + completed.surgeonName() + " is marked completed.", "/my-appointments");
        return completed;
    }

    // ----- Shared -----

    /** One appointment, if the viewer is its patient or its surgeon. */
    public Appointment getForViewer(String id, CurrentUser viewer) {
        if (viewer.role() == Role.PATIENT) {
            return appointments.findByIdAndPatientId(id, viewer.id()).orElseThrow(AppointmentNotFoundException::new);
        }
        if (viewer.role() == Role.DOCTOR) {
            return appointments.findByIdAndSurgeonUserId(id, viewer.id()).orElseThrow(AppointmentNotFoundException::new);
        }
        throw new AppointmentNotFoundException();
    }

    /** Adds each surgeon's current picture and designation. */
    public List<AppointmentResponse> toResponses(List<Appointment> list) {
        Map<String, BreastSurgeonProfile> byId = profiles.findAllById(list.stream().map(Appointment::surgeonId).distinct().toList())
                .stream().collect(Collectors.toMap(BreastSurgeonProfile::id, Function.identity()));
        return list.stream().map(a -> AppointmentResponse.from(a, byId.get(a.surgeonId()))).toList();
    }

    public AppointmentResponse toResponse(Appointment appointment) {
        return toResponses(List.of(appointment)).get(0);
    }

    private Appointment cancel(String ownerField, String ownerId, String id, String reason, String by) {
        Update update = new Update()
                .set("status", AppointmentStatus.CANCELLED)
                .unset("slotKey")
                .set("cancelledAt", Instant.now())
                .set("cancelledBy", by)
                .set("cancelReason", reason);
        Query query = Query.query(Criteria.where("id").is(id).and(ownerField).is(ownerId).and("status").in(ACTIVE));
        Appointment updated = mongo.findAndModify(query, update.set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Appointment.class);
        if (updated == null) {
            throw notChanged(ownerField, ownerId, id, "Only pending or confirmed appointments can be cancelled.");
        }
        String why = reason == null || reason.isBlank() ? "" : " Reason: " + reason.strip();
        if ("PATIENT".equals(by)) {
            notifications.notify(updated.surgeonUserId(), "APPOINTMENT_CANCELLED", "Appointment cancelled",
                    updated.patientName() + " cancelled the appointment on " + when(updated) + "." + why, "/surgeon-dashboard");
        } else {
            notifications.notify(updated.patientId(), "APPOINTMENT_CANCELLED", "Appointment cancelled",
                    updated.surgeonName() + " cancelled your appointment on " + when(updated) + "." + why, "/my-appointments");
        }
        return updated;
    }

    /** "2026-11-02 at 17:30", for notifications. */
    private static String when(Appointment a) {
        return a.date() + " at " + a.time();
    }

    /** Changes the surgeon's own appointment only while it is in one of {@code from}, in a single update. */
    private Appointment transition(String surgeonUserId, String id, List<AppointmentStatus> from, Update update, String refused) {
        Query query = Query.query(Criteria.where("id").is(id).and("surgeonUserId").is(surgeonUserId).and("status").in(from));
        Appointment updated = mongo.findAndModify(query, update.set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Appointment.class);
        if (updated == null) {
            throw notChanged("surgeonUserId", surgeonUserId, id, refused);
        }
        return updated;
    }

    /** Not the caller's appointment: 404. Theirs but in the wrong state: 409 saying what state it is in. */
    private RuntimeException notChanged(String ownerField, String ownerId, String id, String refused) {
        Appointment existing = mongo.findOne(Query.query(Criteria.where("id").is(id).and(ownerField).is(ownerId)), Appointment.class);
        if (existing == null) {
            return new AppointmentNotFoundException();
        }
        return new BookingConflictException(refused + " This one is " + existing.status().name().toLowerCase() + ".");
    }

    /**
     * The slot must be a day and time the surgeon offers, not already past, and at most
     * {@value #BOOKING_WINDOW_DAYS} days ahead. Whether it is free is left to the unique index.
     */
    private void requireBookableSlot(BreastSurgeonProfile profile, String dateText, String timeText) {
        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            throw new FieldValidationException("date", "Enter a real date as YYYY-MM-DD");
        }
        try {
            time = LocalTime.parse(timeText, HH_MM);
        } catch (DateTimeParseException e) {
            throw new FieldValidationException("time", "Enter the time as HH:mm");
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today) || (date.equals(today) && !time.isAfter(LocalTime.now(clock)))) {
            throw new FieldValidationException("date", "That time has already passed. Choose a later slot.");
        }
        if (date.isAfter(today.plusDays(BOOKING_WINDOW_DAYS))) {
            throw new FieldValidationException("date", "Appointments can be booked up to " + BOOKING_WINDOW_DAYS + " days ahead.");
        }
        DayOfWeek day = date.getDayOfWeek();
        if (!profile.availableDays().contains(day.name())) {
            throw new FieldValidationException("date", profile.fullName() + " does not see patients on that day.");
        }
        if (!profile.timeSlots().contains(timeText)) {
            throw new FieldValidationException("time", "That time is not one of the surgeon's slots.");
        }
    }

    private static boolean isSlotClash(DuplicateKeyException e) {
        return String.valueOf(e.getMessage()).contains("slotKey");
    }

    private static BookingConflictException slotTaken() {
        return new BookingConflictException("Sorry, this time slot has just been booked by someone else. Please choose another.");
    }

    /** BSA-yyMMdd-XXXXXX, with the booking date and six characters that cannot be confused (no 0/O, 1/I). */
    private String newCode() {
        StringBuilder code = new StringBuilder("BSA-").append(CODE_DATE.format(LocalDate.now(clock))).append('-');
        for (int i = 0; i < 6; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
