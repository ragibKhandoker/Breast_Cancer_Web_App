package com.cancerweb.backend.dto;

import java.util.List;

import com.cancerweb.backend.model.BreastSurgeonProfile;

/**
 * What patients and visitors see of an approved Breast Surgeon. The login account, personal contact details,
 * verification documents, review notes and date of birth are left out.
 */
public record BreastSurgeonResponse(
        String id,
        String fullName,
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
        String gender,
        List<String> languages) {

    public static BreastSurgeonResponse from(BreastSurgeonProfile p) {
        return new BreastSurgeonResponse(p.id(), p.fullName(), p.specialty(), p.designation(), p.qualifications(),
                p.experienceYears(), p.hospitalName(), p.hospitalAddress(), p.consultationFee(), p.biography(),
                p.registrationNumber(), p.availableDays(), p.timeSlots(), p.isAcceptingAppointments(), photoUrl(p),
                p.gender(), p.languages());
    }

    /** The picture's address, with its file id so a new picture is not hidden by the browser's cache. */
    public static String photoUrl(BreastSurgeonProfile p) {
        return p.photoFileId() == null ? null : "/api/doctors/breast-surgeons/" + p.id() + "/photo?v=" + p.photoFileId();
    }
}
