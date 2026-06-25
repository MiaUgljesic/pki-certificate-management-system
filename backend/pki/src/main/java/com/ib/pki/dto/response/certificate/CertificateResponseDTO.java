package com.ib.pki.dto.response.certificate;

import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.model.enums.CertificateType;
import com.ib.pki.model.enums.RevocationReason;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CertificateResponseDTO {
    private String issuerSerialNumber;
    private CertificateType certificateType;
    private Long id;
    private String serialNumber;
    private String commonName;
    private String organizationName;
    private String organizationalUnit;
    private String country;
    private String email;
    private CertificateStatus status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String keyAlgorithm;
    private int keySize;
    private String signatureAlgorithm;
    private RevocationReason revocationReason;
    private LocalDateTime revokedAt;
    private String SAN;
    private boolean includeSubjectKeyIdentifier;
    private boolean includeAuthorityKeyIdentifier;
    private boolean includeExtendedKeyUsage;
    private boolean hasPrivateKey;
}
