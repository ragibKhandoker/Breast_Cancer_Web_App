package com.cancerweb.backend.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.dto.AppointmentRequest;
import com.cancerweb.backend.exception.BookingConflictException;
import com.cancerweb.backend.model.Appointment;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.AppointmentService;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Feature 17: Breast Surgeon profiles, admin approval, and booking appointments with approved surgeons. */
class BreastSurgeonAppointmentTest extends IntegrationTest {

    private static final String BASE = "/api/doctors/breast-surgeons";
    private static final byte[] PDF = "%PDF-1.4\n% test certificate\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

    @Autowired
    private AppointmentService appointmentService;

    private static String tomorrow() {
        return LocalDate.now(ZoneId.of("Asia/Dhaka")).plusDays(1).toString();
    }

    private static String profileBody(String name, String registration, String specialty) {
        return "{\"fullName\":\"" + name + "\",\"email\":\"chamber@example.com\",\"phone\":\"+8801712345678\","
                + (specialty == null ? "" : "\"specialty\":\"" + specialty + "\",")
                + "\"designation\":\"Consultant Breast Surgeon\",\"qualifications\":\"MBBS, FCPS (Surgery)\","
                + "\"experienceYears\":12,\"hospitalName\":\"Dhaka Breast Centre\",\"hospitalAddress\":\"Dhanmondi, Dhaka\","
                + "\"consultationFee\":1500,\"biography\":\"Breast surgeon focused on early cancer care and reconstruction.\","
                + "\"registrationNumber\":\"" + registration + "\","
                + "\"availableDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],"
                + "\"timeSlots\":[\"10:00\",\"09:00\"]}";
    }

    private String createProfile(Cookie doctor, String name, String registration) throws Exception {
        String body = mvc.perform(withJson(post(BASE + "/profile"), profileBody(name, registration, null)).cookie(doctor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void uploadDocument(Cookie doctor) throws Exception {
        mvc.perform(multipart(BASE + "/profile/documents")
                        .file(new MockMultipartFile("document", "registration.pdf", "application/pdf", PDF))
                        .with(csrf()).cookie(doctor))
                .andExpect(status().isOk());
    }

    private void review(Cookie admin, String profileId, String status, String note) throws Exception {
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + profileId + "/review"),
                        "{\"status\":\"" + status + "\"" + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}").cookie(admin))
                .andExpect(status().isOk());
    }

    /** A doctor with an approved Breast Surgeon profile; returns the profile id. */
    private String approvedSurgeon(Cookie admin, Cookie doctor, String name, String registration) throws Exception {
        String id = createProfile(doctor, name, registration);
        uploadDocument(doctor);
        review(admin, id, "APPROVED", null);
        return id;
    }

    private static String bookingBody(String surgeonId, String date, String time) {
        return "{\"surgeonId\":\"" + surgeonId + "\",\"date\":\"" + date + "\",\"time\":\"" + time + "\",\"reason\":\"Lump check\"}";
    }

    private String book(Cookie patient, String surgeonId, String date, String time) throws Exception {
        String body = mvc.perform(withJson(post("/api/appointments"), bookingBody(surgeonId, date, time)).cookie(patient))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.appointmentCode").value(org.hamcrest.Matchers.matchesPattern("BSA-\\d{6}-[A-Z2-9]{6}")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void onlyDoctorsCreateProfilesAndOnlyForBreastSurgery() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        String body = profileBody("Dr. Nasrin Akter", "BMDC-A-1234", null);

        mvc.perform(withJson(post(BASE + "/profile"), body)).andExpect(status().isUnauthorized());
        mvc.perform(withJson(post(BASE + "/profile"), body).cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(withJson(post(BASE + "/profile"), body).cookie(admin)).andExpect(status().isForbidden());

        // Any other specialty is refused by the server, whatever the page sends.
        mvc.perform(withJson(post(BASE + "/profile"), profileBody("Dr. Nasrin Akter", "BMDC-A-1234", "Cardiology")).cookie(doctor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.specialty").exists());
        // Required fields and the weekly schedule are checked too.
        mvc.perform(withJson(post(BASE + "/profile"), "{\"fullName\":\"\"}").cookie(doctor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.registrationNumber").exists());
        mvc.perform(withJson(post(BASE + "/profile"), profileBody("Dr. Nasrin Akter", "BMDC-A-1234", null)
                        .replace("\"10:00\"", "\"25:99\"")).cookie(doctor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timeSlots").exists());

        mvc.perform(withJson(post(BASE + "/profile"), body).cookie(doctor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.specialty").value("Breast Surgery"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.timeSlots[0]").value("09:00"))
                .andExpect(jsonPath("$.registrationNumber").value("BMDC-A-1234"));
        // One profile per account, and one per registration number.
        mvc.perform(withJson(post(BASE + "/profile"), body).cookie(doctor)).andExpect(status().isConflict());
        Cookie other = signedIn(Role.DOCTOR, "d2@example.com");
        mvc.perform(withJson(post(BASE + "/profile"), profileBody("Dr. Other", "bmdc-a-1234", null)).cookie(other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.registrationNumber").exists());
    }

    @Test
    void onlyApprovedBreastSurgeonsAreListedAndBookable() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String id = createProfile(doctor, "Dr. Nasrin Akter", "BMDC-A-1234");

        // Pending: not listed, not visible, not bookable.
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + id + "/slots")).andExpect(status().isNotFound());
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "09:00")).cookie(patient))
                .andExpect(status().isNotFound());

        // Approval needs a verification document; a rejection needs a note.
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + id + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + id + "/review"), "{\"status\":\"REJECTED\"}").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + id + "/review"), "{\"status\":\"APPROVED\"}").cookie(doctor))
                .andExpect(status().isForbidden());
        uploadDocument(doctor);
        mvc.perform(get("/api/admin/breast-surgeons?status=PENDING").cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documents[0].filename").value("registration.pdf"));
        review(admin, id, "APPROVED", null);

        // Approved: listed for everyone, without private details.
        mvc.perform(get(BASE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Dr. Nasrin Akter"))
                .andExpect(jsonPath("$[0].specialty").value("Breast Surgery"))
                .andExpect(jsonPath("$[0].documents").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());
        mvc.perform(get(BASE).param("q", "nasrin").param("minExperience", "10").param("hospital", "dhaka breast centre"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(BASE + "?minExperience=20")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "?q=karim")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/" + id + "/slots?days=3"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].date").value(tomorrow()))
                .andExpect(jsonPath("$[1].slots[0].time").value("09:00"))
                .andExpect(jsonPath("$[1].slots[0].available").value(true));

        // Editing credentials sends it back for review, and it leaves the listing.
        mvc.perform(withJson(put(BASE + "/profile"), profileBody("Dr. Nasrin Akter", "BMDC-A-9999", null)).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get(BASE)).andExpect(jsonPath("$.length()").value(0));
        review(admin, id, "APPROVED", null);

        // A disabled account, or one that is no longer a doctor, disappears from the listing.
        String doctorId = mongo.findOne(Query.query(Criteria.where("email").is("d@example.com")), User.class).id();
        mongo.updateFirst(Query.query(Criteria.where("id").is(doctorId)), Update.update("role", Role.PATIENT), User.class);
        mvc.perform(get(BASE)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void surgeonManagesOwnProfilePhotoDocumentsAndAvailability() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        mvc.perform(get(BASE + "/profile").cookie(doctor)).andExpect(status().isNotFound());
        String id = approvedSurgeon(admin, doctor, "Dr. Nasrin Akter", "BMDC-A-1234");

        // Not a picture: refused. A PNG: stored and served to anyone.
        mvc.perform(multipart(BASE + "/profile/photo").file(new MockMultipartFile("photo", "me.png", "image/png", PDF))
                        .with(csrf()).cookie(doctor))
                .andExpect(status().isBadRequest());
        String body = mvc.perform(multipart(BASE + "/profile/photo").file(new MockMultipartFile("photo", "me.png", "image/png", PNG))
                        .with(csrf()).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();
        String photoUrl = JsonPath.read(body, "$.photoUrl");
        mvc.perform(get(photoUrl)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));

        // Documents download only for the owner and administrators.
        body = mvc.perform(get(BASE + "/profile").cookie(doctor)).andReturn().getResponse().getContentAsString();
        String documentId = JsonPath.read(body, "$.documents[0].fileId");
        mvc.perform(get(BASE + "/profile/documents/" + documentId).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
        mvc.perform(get("/api/admin/breast-surgeons/" + id + "/documents/" + documentId).cookie(admin)).andExpect(status().isOk());
        Cookie otherDoctor = signedIn(Role.DOCTOR, "d2@example.com");
        mvc.perform(get(BASE + "/profile/documents/" + documentId).cookie(otherDoctor)).andExpect(status().isNotFound());

        // Availability changes keep the approval; pausing bookings keeps the listing but refuses bookings.
        mvc.perform(withJson(put(BASE + "/profile/availability"),
                        "{\"availableDays\":[\"sunday\",\"MONDAY\"],\"timeSlots\":[\"16:30\"],\"acceptingAppointments\":false}").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.availableDays[0]").value("MONDAY"))
                .andExpect(jsonPath("$.acceptingAppointments").value(false));
        mvc.perform(get(BASE)).andExpect(jsonPath("$[0].acceptingAppointments").value(false));
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "16:30")).cookie(patient))
                .andExpect(status().isConflict());

        // Removing a document is a credential change: back to review.
        mvc.perform(delete(BASE + "/profile/documents/" + documentId).with(csrf()).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(0))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void surgeonSetsGenderBirthDateLanguagesAndRemovesPhoto() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        String id = approvedSurgeon(admin, doctor, "Dr. Nasrin Akter", "BMDC-A-1234");
        String birthDate = LocalDate.now().minusYears(45).toString();
        String withExtras = profileBody("Dr. Nasrin Akter", "BMDC-A-1234", null).replace("]}",
                "],\"gender\":\"FEMALE\",\"dateOfBirth\":\"" + birthDate + "\",\"languages\":[\" Bangla \",\"English\",\"bangla\",\"\"]}");

        // Refused: an unknown gender, an age under 21, a language that is not a word.
        mvc.perform(withJson(put(BASE + "/profile"), withExtras.replace("FEMALE", "ROBOT")).cookie(doctor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.gender").exists());
        mvc.perform(withJson(put(BASE + "/profile"), withExtras.replace(birthDate, LocalDate.now().minusYears(10).toString())).cookie(doctor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.dateOfBirth").exists());
        mvc.perform(withJson(put(BASE + "/profile"), withExtras.replace("English", "<b>1</b>")).cookie(doctor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.languages").exists());

        // Saved, tidied, and not a credential change, so the approval stays.
        mvc.perform(withJson(put(BASE + "/profile"), withExtras).cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.dateOfBirth").value(birthDate))
                .andExpect(jsonPath("$.languages.length()").value(2))
                .andExpect(jsonPath("$.languages[0]").value("Bangla"));
        // Patients see gender and languages, not the date of birth.
        mvc.perform(get(BASE + "/" + id))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.languages[1]").value("English"))
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist());

        // Removing the picture clears it; doing it again is harmless.
        mvc.perform(multipart(BASE + "/profile/photo").file(new MockMultipartFile("photo", "me.png", "image/png", PNG))
                        .with(csrf()).cookie(doctor))
                .andExpect(jsonPath("$.photoUrl").exists());
        mvc.perform(delete(BASE + "/profile/photo").with(csrf()).cookie(doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").doesNotExist());
        mvc.perform(get(BASE + "/" + id + "/photo")).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/profile/photo").with(csrf()).cookie(doctor)).andExpect(status().isOk());
        mvc.perform(delete(BASE + "/profile/photo").with(csrf()).cookie(signedIn(Role.PATIENT, "p@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientBooksAndASlotCannotBeTakenTwice() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        String id = approvedSurgeon(admin, doctor, "Dr. Nasrin Akter", "BMDC-A-1234");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie other = signedIn(Role.PATIENT, "p2@example.com");

        // Only patients book.
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "09:00")).cookie(doctor))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "09:00"))).andExpect(status().isUnauthorized());

        // Slots the surgeon does not offer, or in the past, are refused.
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "11:00")).cookie(patient))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.time").exists());
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, "2020-01-01", "09:00")).cookie(patient))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.date").exists());
        String tooFar = LocalDate.now(ZoneId.of("Asia/Dhaka")).plusDays(90).toString();
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tooFar, "09:00")).cookie(patient))
                .andExpect(status().isBadRequest());

        String appointmentId = book(patient, id, tomorrow(), "09:00");
        mvc.perform(get("/api/appointments/my").cookie(patient))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].surgeon.fullName").value("Dr. Nasrin Akter"))
                .andExpect(jsonPath("$[0].surgeon.specialty").value("Breast Surgery"))
                .andExpect(jsonPath("$[0].surgeon.hospitalName").value("Dhaka Breast Centre"))
                .andExpect(jsonPath("$[0].bookedAt").exists());

        // The same slot cannot be booked again, and the calendar shows it taken.
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "09:00")).cookie(other))
                .andExpect(status().isConflict());
        mvc.perform(get(BASE + "/" + id + "/slots?days=2"))
                .andExpect(jsonPath("$[1].slots[0].available").value(false))
                .andExpect(jsonPath("$[1].slots[1].available").value(true));
        // One upcoming appointment per surgeon for each patient.
        mvc.perform(withJson(post("/api/appointments"), bookingBody(id, tomorrow(), "10:00")).cookie(patient))
                .andExpect(status().isConflict());

        // Other patients cannot see or change it.
        mvc.perform(get("/api/appointments/" + appointmentId).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/cancel"), "{}").cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/appointments/my").cookie(other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/appointments/" + appointmentId).cookie(patient)).andExpect(status().isOk());
    }

    @Test
    void twoPatientsBookingTheSameSlotAtOnceGetOneAppointment() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        String id = approvedSurgeon(admin, doctor, "Dr. Nasrin Akter", "BMDC-A-1234");
        List<CurrentUser> patients = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            User u = createUser("Patient " + i, "p" + i + "@example.com", Role.PATIENT);
            patients.add(new CurrentUser(u.id(), u.email(), u.fullName(), Role.PATIENT));
        }
        ExecutorService pool = Executors.newFixedThreadPool(patients.size());
        try {
            List<Callable<Boolean>> attempts = patients.stream().<Callable<Boolean>>map(p -> () -> {
                try {
                    appointmentService.book(p, new AppointmentRequest(id, tomorrow(), "09:00", null));
                    return true;
                } catch (BookingConflictException e) {
                    return false;
                }
            }).toList();
            long booked = 0;
            for (Future<Boolean> result : pool.invokeAll(attempts)) {
                booked += result.get() ? 1 : 0;
            }
            assertThat(booked).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(mongo.count(new Query(), Appointment.class)).isEqualTo(1);
    }

    @Test
    void surgeonConfirmsPatientReschedulesAndCancels() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie otherDoctor = signedIn(Role.DOCTOR, "d2@example.com");
        String id = approvedSurgeon(admin, doctor, "Dr. Nasrin Akter", "BMDC-A-1234");
        approvedSurgeon(admin, otherDoctor, "Dr. Karim", "BMDC-A-5678");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie other = signedIn(Role.PATIENT, "p2@example.com");
        String appointmentId = book(patient, id, tomorrow(), "09:00");

        // The surgeon sees the request with the patient's details; another surgeon does not.
        mvc.perform(get("/api/appointments/doctor").cookie(doctor))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patient.fullName").value("Patient User"))
                .andExpect(jsonPath("$[0].reason").value("Lump check"));
        mvc.perform(get("/api/appointments/doctor").cookie(otherDoctor)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/confirm"), "{}").cookie(otherDoctor))
                .andExpect(status().isNotFound());
        // Patients cannot confirm or complete.
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/confirm"), "{}").cookie(patient))
                .andExpect(status().isForbidden());

        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/confirm"), "{}").cookie(doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/confirm"), "{}").cookie(doctor))
                .andExpect(status().isConflict());
        // Not before the day.
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/complete"), "{}").cookie(doctor))
                .andExpect(status().isConflict());

        // Rescheduling: never onto a taken slot; the new time waits for the surgeon again; the old slot is freed.
        String otherAppointment = book(other, id, tomorrow(), "10:00");
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/reschedule"),
                        "{\"date\":\"" + tomorrow() + "\",\"time\":\"10:00\"}").cookie(patient))
                .andExpect(status().isConflict());
        String later = LocalDate.now(ZoneId.of("Asia/Dhaka")).plusDays(2).toString();
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/reschedule"),
                        "{\"date\":\"" + later + "\",\"time\":\"09:00\"}").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.date").value(later))
                .andExpect(jsonPath("$.previousDate").value(tomorrow()))
                .andExpect(jsonPath("$.rescheduleCount").value(1));
        mvc.perform(get(BASE + "/" + id + "/slots?days=3"))
                .andExpect(jsonPath("$[1].slots[0].available").value(true))
                .andExpect(jsonPath("$[2].slots[0].available").value(false));

        // The surgeon rejects the other request; the patient cancels theirs. Neither can be cancelled twice.
        mvc.perform(withJson(put("/api/appointments/" + otherAppointment + "/cancel"), "{\"reason\":\"Away that day\"}").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("SURGEON"))
                .andExpect(jsonPath("$.cancelReason").value("Away that day"));
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/cancel"), "{}").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledBy").value("PATIENT"));
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/cancel"), "{}").cookie(patient))
                .andExpect(status().isConflict());
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/reschedule"),
                        "{\"date\":\"" + tomorrow() + "\",\"time\":\"09:00\"}").cookie(patient))
                .andExpect(status().isConflict());
        // Freed slots can be booked again.
        book(other, id, tomorrow(), "10:00");
    }

    @Test
    void surgeonCompletesAConfirmedAppointmentOnOrAfterItsDay() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        String id = approvedSurgeon(admin, doctor, "Dr. Nasrin Akter", "BMDC-A-1234");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String appointmentId = book(patient, id, tomorrow(), "09:00");
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/confirm"), "{}").cookie(doctor)).andExpect(status().isOk());

        // Move it into the past, as if the day had come and gone.
        String yesterday = LocalDate.now(ZoneId.of("Asia/Dhaka")).minusDays(1).toString();
        mongo.updateFirst(Query.query(Criteria.where("id").is(appointmentId)),
                new Update().set("date", yesterday).set("slotKey", Appointment.slotKey(id, yesterday, "09:00")), Appointment.class);

        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/complete"), "{}").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/cancel"), "{}").cookie(patient))
                .andExpect(status().isConflict());
        Document stored = mongo.getCollection("appointments").find().first();
        assertThat(stored.getString("status")).isEqualTo("COMPLETED");
    }
}
