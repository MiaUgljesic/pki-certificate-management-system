package com.ib.pki.dto.response.csr;

import lombok.*;

import java.security.PublicKey;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CertificateSigningRequest {
    private String commonName;
    private String organization;
    private String organizationalUnit;
    private String country;
    private String email;
    private PublicKey publicKey;
}
