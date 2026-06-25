package com.ib.pki.dto.request.auth;

import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TwoFactorLoginRequest {
    String email;
    String password;
    String code;
}
