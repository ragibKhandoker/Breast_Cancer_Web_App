package com.cancerweb.backend.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST} and {@code PUT /api/doctors/breast-surgeons/profile}. {@code specialty} may be left out;
 * if sent it must be "Breast Surgery" (BreastSurgeonService checks it, along with the days and time slots).
 * There is no status, owner or review field, so a surgeon cannot approve their own profile.
 * {@code gender}, {@code dateOfBirth} and {@code languages} are optional; BreastSurgeonService checks the date
 * of birth and tidies the languages.
 * The rules match BreastSurgeonProfileForm in the frontend.
 */
public record BreastSurgeonProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters") String fullName,

        @NotBlank(message = "Email is required")
        @Size(max = 254, message = "Email is too long")
        @Pattern(regexp = ValidationRules.EMAIL, message = "Enter a valid email address") String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number, like +8801712345678")
        String phone,

        String specialty,

        @NotBlank(message = "Designation is required")
        @Size(max = 100, message = "Designation must be 100 characters or fewer") String designation,

        @NotBlank(message = "Degrees and qualifications are required")
        @Size(max = 300, message = "Qualifications must be 300 characters or fewer") String qualifications,

        @NotNull(message = "Years of experience are required")
        @Min(value = 0, message = "Experience cannot be negative")
        @Max(value = 60, message = "Experience must be 60 years or fewer") Integer experienceYears,

        @NotBlank(message = "Hospital or chamber name is required")
        @Size(max = 150, message = "Hospital name must be 150 characters or fewer") String hospitalName,

        @NotBlank(message = "Hospital address is required")
        @Size(max = 300, message = "Address must be 300 characters or fewer") String hospitalAddress,

        @NotNull(message = "Consultation fee is required")
        @Min(value = 0, message = "Fee cannot be negative")
        @Max(value = 1000000, message = "Fee must be 1,000,000 or less") Integer consultationFee,

        @NotBlank(message = "A short biography is required")
        @Size(min = 30, max = 2000, message = "Biography must be 30 to 2000 characters") String biography,

        @NotBlank(message = "Medical registration number is required")
        @Pattern(regexp = "^[A-Za-z0-9/ -]{3,40}$", message = "Use 3 to 40 letters, numbers, spaces, / or -")
        String registrationNumber,

        List<String> availableDays,

        List<String> timeSlots,

        @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Choose Male, Female or Other") String gender,

        LocalDate dateOfBirth,

        List<String> languages) {

    public BreastSurgeonProfileRequest {
        fullName = strip(fullName);
        email = strip(email);
        phone = strip(phone);
        specialty = strip(specialty);
        designation = strip(designation);
        qualifications = strip(qualifications);
        hospitalName = strip(hospitalName);
        hospitalAddress = strip(hospitalAddress);
        biography = strip(biography);
        registrationNumber = strip(registrationNumber);
        gender = strip(gender);
        if (gender != null && gender.isEmpty()) {
            gender = null;
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
