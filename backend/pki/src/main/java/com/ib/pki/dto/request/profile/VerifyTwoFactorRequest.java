package com.ib.pki.dto.request.profile;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class VerifyTwoFactorRequest {
    private String code;
}
