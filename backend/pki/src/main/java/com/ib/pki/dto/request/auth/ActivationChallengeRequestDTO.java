package com.ib.pki.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivationChallengeRequestDTO {
    @NotBlank(message = "Token is missing")
    private String token;

    @NotBlank(message = "Decrypted challenge is missing")
    private String decryptedChallenge;
}
