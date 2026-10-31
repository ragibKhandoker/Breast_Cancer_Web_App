package com.cancerweb.backend.dto;

import java.util.Locale;

import com.cancerweb.backend.model.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}. {@code accountType} is PATIENT (the default when left out) or DOCTOR;
 * an administrator account can never be made here, and a "role" field sent anyway is ignored. A new DOCTOR
 * account waits for an administrator's verification (see AuthService), and must be a breast surgeon or breast
 * specialist: {@code doctorSpecialty} BREAST_SURGEON or BREAST_SPECIALIST, and {@code confirmBreastSpecialist}
 * true. Both are ignored for patients. The rules match the Register form.
 */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters") String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = ValidationRules.EMAIL, message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String email,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationRules.PASSWORD, message = ValidationRules.PASSWORD_MESSAGE) String password,

        @Pattern(regexp = "^(PATIENT|DOCTOR)$", message = "Choose to register as a patient or a doctor") String accountType,

        String doctorSpecialty,

        Boolean confirmBreastSpecialist) {

    /** The only doctors who may register here: this site is for breast care. Values and the labels stored. */
    public static final java.util.Map<String, String> DOCTOR_SPECIALTIES = java.util.Map.of(
            "BREAST_SURGEON", "Breast Surgeon",
            "BREAST_SPECIALIST", "Breast Specialist");


    /** Surrounding spaces (from copy and paste) are not an error; the service also lower-cases the email. */
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip();
        accountType = accountType == null || accountType.isBlank() ? "PATIENT" : accountType.strip().toUpperCase(Locale.ROOT);
        doctorSpecialty = doctorSpecialty == null ? null : doctorSpecialty.strip().toUpperCase(Locale.ROOT);
    }

    /** The role the new account gets: DOCTOR when asked for, otherwise PATIENT. */
    public Role role() {
        return "DOCTOR".equals(accountType) ? Role.DOCTOR : Role.PATIENT;
    }
}
