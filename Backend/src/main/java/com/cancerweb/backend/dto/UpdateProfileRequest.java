package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/profile}. Only these fields can change here: there is no role, email or password,
 * so a request cannot raise its own role. Blank optional fields clear the value. specialization and hospital
 * are kept for doctors only and ignored for other roles. The rules match the Profile page in the frontend.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters") String fullName,

        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number, like +8801712345678")
        String phone,

        @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "Enter the date as YYYY-MM-DD") String dateOfBirth,

        @Pattern(regexp = "^$|^(FEMALE|MALE|OTHER|PREFER_NOT_TO_SAY)$", message = "Choose one of the listed options")
        String gender,

        @Size(max = 300, message = "Address must be 300 characters or fewer") String address,

        @Size(max = 100, message = "Specialization must be 100 characters or fewer") String specialization,

        @Size(max = 150, message = "Hospital must be 150 characters or fewer") String hospital) {

    public UpdateProfileRequest {
        fullName = strip(fullName);
        phone = strip(phone);
        dateOfBirth = strip(dateOfBirth);
        gender = strip(gender);
        address = strip(address);
        specialization = strip(specialization);
        hospital = strip(hospital);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
