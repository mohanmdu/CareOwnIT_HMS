package com.pms.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** recaptchaToken is only checked when app.contact.recaptcha.enabled=true (see RecaptchaVerificationService) - harmless to omit otherwise. */
public record ContactRequest(
        @NotBlank(message = "Name is required.") @Size(max = 150) String name,
        @NotBlank(message = "Email is required.") @Email(message = "Enter a valid email address.") @Size(max = 255)
                String email,
        @NotBlank(message = "Hospital/Clinic name is required.") @Size(max = 200) String hospitalName,
        @Size(max = 4000) String message,
        String recaptchaToken) {}
