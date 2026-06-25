package com.ib.pki.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivationResponseDTO {
    private String email;
    private String message; // Npr: "Account activated successfully."
}
