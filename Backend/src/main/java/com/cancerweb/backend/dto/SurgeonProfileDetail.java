package com.cancerweb.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.cancerweb.backend.model.BreastSurgeonProfile;
import com.cancerweb.backend.model.CredentialDocument;
import com.cancerweb.backend.model.SurgeonProfileStatus;

/**
 * The whole Breast Surgeon profile, for its owner and for administrators reviewing it: contact details,
 * verification documents and where the review stands.
 */
public record SurgeonProfileDetail(
        String id,
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
        boolean acceptingAppointments,
        String photoUrl,
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

    public static SurgeonProfileDetail from(BreastSurgeonProfile p) {
        return new SurgeonProfileDetail(p.id(), p.userId(), p.fullName(), p.email(), p.phone(), p.specialty(),
                p.designation(), p.qualifications(), p.experienceYears(), p.hospitalName(), p.hospitalAddress(),
                p.consultationFee(), p.biography(), p.registrationNumber(), p.availableDays(), p.timeSlots(),
                p.isAcceptingAppointments(), BreastSurgeonResponse.photoUrl(p), p.documents(), p.status(),
                p.reviewNote(), p.reviewedBy(), p.reviewedAt(), p.submittedAt(), p.createdAt(), p.updatedAt(),
                p.gender(), p.dateOfBirth(), p.languages());
    }
}
