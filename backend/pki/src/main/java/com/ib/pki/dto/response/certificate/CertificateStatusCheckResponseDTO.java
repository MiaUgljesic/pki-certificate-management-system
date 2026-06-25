package com.ib.pki.dto.response.certificate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateStatusCheckResponseDTO {
    private String serialNumber;
    private String crlUrl;
    private boolean isRevoked;
    private String reason;
}