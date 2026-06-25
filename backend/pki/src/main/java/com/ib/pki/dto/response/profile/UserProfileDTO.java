package com.ib.pki.dto.response.profile;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class UserProfileDTO {
    String email;
    String name;
    String surname;
    String organization;
    String publicKey;
    Boolean isTwoFactorEnabled;
}