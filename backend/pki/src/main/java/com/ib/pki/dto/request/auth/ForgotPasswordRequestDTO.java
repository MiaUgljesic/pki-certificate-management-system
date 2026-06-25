package com.ib.pki.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequestDTO {
    @Email(message = "Email format is invalid")
    @NotBlank(message = "Email is required")
    private String email;
}
