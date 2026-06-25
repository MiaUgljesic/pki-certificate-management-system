package com.ib.pki.dto.request.csr;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Date;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutogenerateCertificateSigningRequest {

    @NotBlank(message = "Common Name is required")
    @Size(max = 255, message = "Common Name must be less than 255 characters")
    private String commonName;

    @NotBlank(message = "Organization is required")
    @Size(max = 255, message = "Organization must be less than 255 characters")
    private String organization;

    @Size(max = 255, message = "Organizational Unit must be less than 255 characters")
    private String organizationalUnit;

    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter ISO code (e.g. RS, US)")
    private String country;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Issuer serial number is required")
    private String issuerSerialNumber;

    @NotBlank(message = "Keystore password is required")
    private String keyStorePassword;

    private String alias;

    @NotNull(message = "Valid to date is required")
    @Future(message = "Valid to date must be in the future")
    private Date validTo;

    private Boolean includeSubjectKeyIdentifier;
    private Boolean includeAuthorityKeyIdentifier;
    private Boolean includeExtendedKeyUsage;
}