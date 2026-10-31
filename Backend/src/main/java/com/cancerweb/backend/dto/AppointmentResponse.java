package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.Appointment;
import com.cancerweb.backend.model.AppointmentStatus;
import com.cancerweb.backend.model.BreastSurgeonProfile;

/**
 * An appointment as the patient and the surgeon see it. The surgeon's picture and designation come from the
 * current profile when it still exists; names, hospital and fee are as they were when it was booked.
 */
public record AppointmentResponse(
        String id,
        String appointmentCode,
        String date,
        String time,
        AppointmentStatus status,
        String reason,
        Instant bookedAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelledBy,
        String cancelReason,
        String previousDate,
        String previousTime,
        int rescheduleCount,
        Patient patient,
        Surgeon surgeon) {

    public record Patient(String id, String fullName, String email, String phone) {
    }

    public record Surgeon(String id, String fullName, String specialty, String designation, String hospitalName,
            String hospitalAddress, Integer consultationFee, String photoUrl) {
    }

    public static AppointmentResponse from(Appointment a, BreastSurgeonProfile profile) {
        Surgeon surgeon = new Surgeon(a.surgeonId(), a.surgeonName(), BreastSurgeonProfile.SPECIALTY,
                profile == null ? null : profile.designation(), a.hospitalName(),
                profile == null ? null : profile.hospitalAddress(), a.consultationFee(),
                profile == null ? null : BreastSurgeonResponse.photoUrl(profile));
        return new AppointmentResponse(a.id(), a.appointmentCode(), a.date(), a.time(), a.status(), a.reason(),
                a.bookedAt(), a.updatedAt(), a.confirmedAt(), a.completedAt(), a.cancelledAt(), a.cancelledBy(),
                a.cancelReason(), a.previousDate(), a.previousTime(),
                a.rescheduleCount() == null ? 0 : a.rescheduleCount(),
                new Patient(a.patientId(), a.patientName(), a.patientEmail(), a.patientPhone()), surgeon);
    }
}
