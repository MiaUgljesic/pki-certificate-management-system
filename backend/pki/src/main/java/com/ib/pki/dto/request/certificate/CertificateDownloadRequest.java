package com.ib.pki.dto.request.certificate;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @Builder
public class CertificateDownloadRequest {
    private @NotNull @NotEmpty String serialNumber;
    private @NotNull @NotEmpty String keyStorePassword; // Lozinka koju korisnik bira za svoj .p12 fajl
    private String alias;
}
