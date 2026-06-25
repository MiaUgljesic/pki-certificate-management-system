package com.ib.pki.dto.request.certificate;

import com.ib.pki.model.enums.CertificateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateRequestDTO {

    @NotBlank(message = "Common Name is required")
    private String commonName;

    @NotBlank(message = "Organization is required")
    private String organization;

    private String organizationalUnit;
    private String locality;
    private String country;
    private String email;

    @NotNull(message = "Expiration date is required")
    private Date validTo;

    @NotNull(message = "Certificate type is required")
    private CertificateType type;

    // Can be null if type is ROOT
    private String issuerSerialNumber;

    @NotNull(message = "Subject Alternate Name is required")
    private String SAN;

    // Extension flags - allow users to select which standard extensions to include
    private boolean includeSubjectKeyIdentifier = false;
    private boolean includeAuthorityKeyIdentifier = false;
    private boolean includeExtendedKeyUsage = false;
}