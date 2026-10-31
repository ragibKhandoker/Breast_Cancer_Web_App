package com.cancerweb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The professional profile of a doctor account that offers Breast Surgery consultations. One per account
 * (unique index on {@code userId}). The site books appointments with Breast Surgeons only, so {@code specialty}
 * is always {@link #SPECIALTY}: the API takes no other value, and listing and booking check it again.
 *
 * <p>Weekly availability: {@code availableDays} are DayOfWeek names (MONDAY, ...), and every one of those days
 * offers the same {@code timeSlots} ("HH:mm", in the clinic time zone, app.appointments.time-zone).
 * {@code acceptingAppointments} lets the surgeon pause new bookings without leaving the listing.
 *
 * @param email           professional contact address, which may differ from the login email
 * @param consultationFee in Bangladeshi taka
 * @param photoFileId     GridFS id of the profile picture, or null
 * @param reviewNote      the administrator's reason for a rejection, shown to the surgeon
 * @param gender          MALE, FEMALE or OTHER, or null when not given (profiles made before it existed)
 * @param dateOfBirth     optional; shown to administrators only
 * @param languages       languages the surgeon consults in, such as Bangla and English; may be empty
 */
@Document("breast_surgeon_profiles")
public record BreastSurgeonProfile(
        @Id String id,
        String userId,
        String fullName,
        String email,
        String phone,
        String specialty,
        String designation,
        String qualifications,
        Integer experienceYears,
        String hospitalName,
        String hospitalAddress,
        Integer consultationFee,
        String biography,
        String registrationNumber,
        List<String> availableDays,
        List<String> timeSlots,
        Boolean acceptingAppointments,
        String photoFileId,
        String photoContentType,
        List<CredentialDocument> documents,
        SurgeonProfileStatus status,
        String reviewNote,
        String reviewedBy,
        Instant reviewedAt,
        Instant submittedAt,
        Instant createdAt,
        Instant updatedAt,
        String gender,
        LocalDate dateOfBirth,
        List<String> languages) {

    public static final String SPECIALTY = "Breast Surgery";

    public List<String> availableDays() {
        return availableDays == null ? List.of() : availableDays;
    }

    public List<String> timeSlots() {
        return timeSlots == null ? List.of() : timeSlots;
    }

    public List<String> languages() {
        return languages == null ? List.of() : languages;
    }

    public List<CredentialDocument> documents() {
        return documents == null ? List.of() : documents;
    }

    public boolean isAcceptingAppointments() {
        return !Boolean.FALSE.equals(acceptingAppointments);
    }

    /** Approved, and a Breast Surgery profile; the account itself is checked separately. */
    public boolean isApprovedBreastSurgeon() {
        return status == SurgeonProfileStatus.APPROVED && SPECIALTY.equals(specialty);
    }
}
