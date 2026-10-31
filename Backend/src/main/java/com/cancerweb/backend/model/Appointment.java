package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A patient's consultation with a Breast Surgeon. Patient, surgeon and hospital details are copied in when it
 * is booked, so the record still reads correctly if a profile changes later.
 *
 * <p>{@code slotKey} ("surgeonId|date|time") is set while the appointment holds its slot (PENDING or CONFIRMED,
 * and kept once COMPLETED) and removed on cancellation. A unique index on it (see DatabaseInitializer) is what
 * stops two bookings or reschedules from taking the same slot, even when they arrive at the same moment.
 *
 * @param appointmentCode the readable Appointment ID shown to people, e.g. BSA-261031-7KQ2XM
 * @param surgeonId       the Breast Surgeon profile id
 * @param surgeonUserId   the surgeon's account id, for the surgeon's own list and actions
 * @param date            ISO date (yyyy-MM-dd) in the clinic time zone
 * @param time            HH:mm in the clinic time zone
 * @param cancelledBy     PATIENT or SURGEON
 * @param previousDate    the date before the last reschedule
 */
@Document("appointments")
public record Appointment(
        @Id String id,
        String appointmentCode,
        String patientId,
        String patientName,
        String patientEmail,
        String patientPhone,
        String surgeonId,
        String surgeonUserId,
        String surgeonName,
        String hospitalName,
        Integer consultationFee,
        String date,
        String time,
        String reason,
        AppointmentStatus status,
        String slotKey,
        Instant bookedAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        String previousDate,
        String previousTime,
        Integer rescheduleCount) {

    public static String slotKey(String surgeonId, String date, String time) {
        return surgeonId + "|" + date + "|" + time;
    }
}
