package com.ib.pki.service;
import com.ib.pki.dto.request.certificate.CertificateRequestDTO;
import com.ib.pki.dto.response.certificate.CaSuggestionResponseDTO;
import com.ib.pki.dto.response.certificate.CertificateOverviewDTO;
import com.ib.pki.dto.response.certificate.CertificateResponseDTO;
import com.ib.pki.model.Certificate;
import com.ib.pki.model.CertificateExtension;
import com.ib.pki.model.Organization;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.model.enums.CertificateType;
import com.ib.pki.model.enums.UserRole;
import com.ib.pki.repository.CertificateRepository;
import com.ib.pki.repository.OrganizationRepository;
import com.ib.pki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final CertificateValidationService validationService;
    private final CertificateEncryptionService certificateEncryptionService;

    /**
     * Retrieves all certificates from the database and maps them to DTOs with
     * paging and sorting support. Optionally filters by date.
     */
    public Page<CertificateResponseDTO> getAllCertificates(Pageable pageable, Long date) {
        Page<Certificate> page;
        
        if (date != null) {
            LocalDateTime startOfDay = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneId.systemDefault()).toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.toLocalDate().atTime(23, 59, 59);
            page = certificateRepository.findCertificatesWithDateRange(startOfDay, endOfDay, pageable);
        } else {
            page = certificateRepository.findAll(pageable);
        }
        
        return page.map(this::mapToDTO);
    }

    public Page<CertificateResponseDTO> getOrganizationCertificates(User user, Pageable pageable, Long date) {
        if (user.getOrganization() == null) {
            throw new RuntimeException("User organization not found.");
        }

        Page<Certificate> page;
        if (date != null) {
            LocalDateTime startOfDay = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneId.systemDefault()).toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.toLocalDate().atTime(23, 59, 59);
            page = certificateRepository.findCertificatesByOrganizationIdAndDate(user.getOrganization().getId(), startOfDay, endOfDay, pageable);
        } else {
            page = certificateRepository.findCertificatesByOrganizationId(user.getOrganization().getId(), pageable);
        }

        return page.map(this::mapToDTO);
    }

    public CertificateResponseDTO mapToDTO(Certificate cert) {
        CertificateResponseDTO.CertificateResponseDTOBuilder builder = CertificateResponseDTO.builder()
                .issuerSerialNumber(cert.getIssuer() != null ? cert.getIssuer().getSerialNumber() : null)
                .certificateType(cert.getType())
                .id(cert.getId())
                .serialNumber(cert.getSerialNumber())
                .commonName(cert.getCommonName())
                .organizationName(cert.getOrganizationName())
                .organizationalUnit(cert.getOrganizationalUnit())
                .country(cert.getCountry())
                .email(cert.getEmail())
                .status(cert.getStatus())
                .validFrom(cert.getValidFrom())
                .validTo(cert.getValidTo())
                .keyAlgorithm(cert.getKeyAlgorithm())
                .keySize(cert.getKeySize() != null ? cert.getKeySize() : 0)
                .signatureAlgorithm(cert.getSignatureAlgorithm())
                .revocationReason(cert.getRevocationReason())
                .revokedAt(cert.getRevokedAt()).hasPrivateKey(cert.getPrivateKeyEncrypted() != null);

        applyExtensionData(builder, cert);
        CertificateResponseDTO res = builder.build();
        return res;
    }


    public Page<CertificateOverviewDTO> getUserCertificates(User user, Pageable pageable, Long date) {
        List<CertificateOverviewDTO> certificateOverview = new ArrayList<>();
        Page<Certificate> certificates = getCertificates(user, pageable, date);
        for(Certificate certificate : certificates.getContent()){
            List<CertificateExtension> extensions = certificate.getExtensions();
            CertificateOverviewDTO.CertificateOverviewDTOBuilder dtoBuilder = CertificateOverviewDTO.builder()
                    .id(certificate.getId()).serialNumber(certificate.getSerialNumber()).issuerSerialNumber(certificate.getIssuer() != null ? certificate.getIssuer().getSerialNumber() : null)
                    .commonName(certificate.getCommonName()).organizationName(certificate.getOrganizationName())
                    .organizationalUnit(certificate.getOrganizationalUnit()).country(certificate.getCountry())
                    .email(certificate.getEmail()).status(certificate.getStatus()).validFrom(certificate.getValidFrom())
                    .validTo(certificate.getValidTo()).keyAlgorithm(certificate.getKeyAlgorithm()).keySize(certificate.getKeySize())
                    .signatureAlgorithm(certificate.getSignatureAlgorithm()).revocationReason(certificate.getRevocationReason())
                    .revokedAt(certificate.getRevokedAt()).type(certificate.getType()).hasPrivateKey(certificate.getPrivateKeyEncrypted() != null)
                    .includeSubjectKeyIdentifier(hasExtension(extensions, Extension.subjectKeyIdentifier.getId()))
                    .includeAuthorityKeyIdentifier(hasExtension(extensions, Extension.authorityKeyIdentifier.getId()))
                    .includeExtendedKeyUsage(hasExtension(extensions, Extension.extendedKeyUsage.getId()));
            applyExtensionData(dtoBuilder, certificate);
            certificateOverview.add(dtoBuilder.build());
        }
        return new PageImpl(certificateOverview, pageable, certificates.getTotalElements());
    }

    private Page<Certificate> getCertificates(User user, Pageable pageable, Long date) {
        LocalDateTime startOfDay = null;
        LocalDateTime endOfDay = null;
        if (date != null) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneId.systemDefault());
            startOfDay = dateTime.toLocalDate().atStartOfDay();
            endOfDay = dateTime.toLocalDate().atTime(23, 59, 59);
        }
        return date != null ?
                findUserCertificatesWithAllRelationsAndDate(user, startOfDay, endOfDay, pageable) :
                findUserCertificatesWithAllRelations(user, pageable);
    }

    private Page<Certificate> findUserCertificatesWithAllRelations(User user, Pageable pageable) {
        return certificateRepository.findUserCertificatesWithAllRelations(user.getId(), pageable);
    }

    private Page<Certificate> findUserCertificatesWithAllRelationsAndDate(User user, LocalDateTime startOfDay, LocalDateTime endOfDay, Pageable pageable) {
        return certificateRepository.findUserCertificatesWithAllRelationsAndDate(user.getId(), startOfDay, endOfDay, pageable);
    }


    /**
     * Main entry point for issuing certificates.
     * It performs validation for subordinates and calls generation for Root.
     */
    public X509Certificate issueCertificate(CertificateRequestDTO dto) throws Exception {
        User currentUser = loadCurrentUser();

        if (dto.getType() == CertificateType.ROOT) {
            if (currentUser.getRole() != UserRole.ADMIN) {
                throw new Exception("Only ADMIN can issue ROOT certificates.");
            }

            Organization organization = organizationRepository.findByName(dto.getOrganization())
                    .orElseThrow(() -> new Exception("Organization not found: " + dto.getOrganization()));
            generateRootCertificate(dto, organization);
            return null;
        }

        if (dto.getIssuerSerialNumber() == null || dto.getIssuerSerialNumber().isBlank()) {
            throw new Exception("Issuer serial number is required for non-root certificates.");
        }

        Certificate issuerEntity = certificateRepository.findBySerialNumber(dto.getIssuerSerialNumber())
                .orElseThrow(() -> new Exception("Issuer certificate not found in database."));


        Organization issuerOrganization = issuerEntity.getOrganization();
        if (issuerOrganization == null ){
            throw new Exception("Issuer organization not found in database.");
        }
        if (currentUser.getRole() != UserRole.ADMIN) {
            if ( currentUser.getOrganization() == null
                    || !issuerOrganization.getId().equals(currentUser.getOrganization().getId())) {
                throw new Exception("You can issue certificates only for your own organization.");
            }

            if (!issuerOrganization.getName().equals(dto.getOrganization())) {
                throw new Exception("Requested organization must match issuer's organization.");
            }
        }

        validationService.validateIssuerChain(issuerEntity, dto.getValidTo());
        return generateSignedCertificate(dto, issuerEntity, issuerOrganization);
    }

    public X509Certificate convertToX509Certificate(String certificateData) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(certificateData);
        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
    }

    /**
     * Root CA Generation
     * Creates a self-signed Root certificate and persists it securely.
     */
    public X509Certificate generateRootCertificate(CertificateRequestDTO dto, Organization organization)
            throws Exception {
        KeyPair keyPair = generateKeyPair();
        X500Name name = buildSubjectName(dto);

        BigInteger rootSerialNumber = generateSerialNumber();

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                name,
                rootSerialNumber,
                new Date(),
                dto.getValidTo(),
                name,
                keyPair.getPublic());

        addStandardExtensions(certBuilder, dto, keyPair.getPublic(), keyPair.getPublic(), rootSerialNumber.toString());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        X509Certificate root = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        saveCertificateToDatabase(root, keyPair.getPrivate(), dto, organization, null);

        return root;
    }

    public X509Certificate generateSignedCertificate(CertificateRequestDTO dto, Certificate issuerEntity,
                                                     Organization organization) throws Exception {
        KeyPair keyPair = generateKeyPair();
        X509Certificate issuerX509 = convertToX509Certificate(issuerEntity.getCertificateData());

        X500Name issuerName = X500Name.getInstance(issuerX509.getSubjectX500Principal().getEncoded());
        X500Name subject = buildSubjectName(dto);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName,
                generateSerialNumber(),
                new Date(),
                dto.getValidTo(),
                subject,
                keyPair.getPublic());

        addStandardExtensions(certBuilder, dto, keyPair.getPublic(), issuerX509.getPublicKey(), issuerEntity.getSerialNumber());

        PrivateKey issuerPrivateKey = loadPrivateKey(issuerEntity, organization);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC")
                .build(issuerPrivateKey);

        X509Certificate signed = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        saveCertificateToDatabase(signed, keyPair.getPrivate(), dto, organization, issuerEntity);

        return signed;
    }

    /**
     * Secure Persistence
     * Maps the cryptographic data to the database model and encrypts the private
     * key.
     */
    public void saveCertificateToDatabase(X509Certificate x509Cert, PrivateKey privateKey,
            CertificateRequestDTO dto, Organization organization,
            Certificate issuerEntity) throws Exception {

        // encrypt the private key using the Organization's key before saving
        String encryptedKey = certificateEncryptionService.encryptPrivateKey(privateKey.getEncoded(), organization);

        System.out.println("Encrypting private key passed");

        String encodedCert = Base64.getEncoder().encodeToString(x509Cert.getEncoded());

        System.out.println("Building extension entities passed");

        Certificate certEntity = Certificate.builder()
                .serialNumber(x509Cert.getSerialNumber().toString())
                .commonName(dto.getCommonName())
                .organizationName(dto.getOrganization())
                .organizationalUnit(dto.getOrganizationalUnit())
                .country(dto.getCountry())
                .email(dto.getEmail())
                .type(dto.getType())
                .status(CertificateStatus.ACTIVE)
                .validFrom(LocalDateTime.now())
                .validTo(dto.getValidTo().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .certificateData(encodedCert)
                .privateKeyEncrypted(encryptedKey)
                .organization(organization)
                .issuer(issuerEntity)
                .keyAlgorithm("RSA")
                .keySize(2048)
                .signatureAlgorithm("SHA256WithRSAEncryption")
                .build();

        certEntity.setExtensions(buildExtensionsFromCertificate(x509Cert, certEntity));

        System.out.println("Linking extensions passed");

        certificateRepository.save(certEntity);
    }

    public List<CertificateExtension> buildExtensionsFromCertificate(X509Certificate x509Cert, Certificate certEntity) throws Exception {
        List<CertificateExtension> extensions = new ArrayList<>();
        addExtensionIfPresent(extensions, certEntity, x509Cert, Extension.subjectAlternativeName.getId());
        addExtensionIfPresent(extensions, certEntity, x509Cert, Extension.subjectKeyIdentifier.getId());
        addExtensionIfPresent(extensions, certEntity, x509Cert, Extension.authorityKeyIdentifier.getId());
        addExtensionIfPresent(extensions, certEntity, x509Cert, Extension.extendedKeyUsage.getId());
        addExtensionIfPresent(extensions, certEntity, x509Cert, Extension.cRLDistributionPoints.getId());
        return extensions;
    }

    private void addExtensionIfPresent(List<CertificateExtension> extensions, Certificate certEntity, X509Certificate x509Cert, String oid) throws Exception {
        byte[] value = x509Cert.getExtensionValue(oid);
        if (value == null) {
            return;
        }

        boolean critical = x509Cert.getCriticalExtensionOIDs() != null
                && x509Cert.getCriticalExtensionOIDs().contains(oid);
        String encodedValue = Base64.getEncoder().encodeToString(value);

        extensions.add(CertificateExtension.builder()
                .certificate(certEntity)
                .extensionOid(oid)
                .extensionValue(encodedValue)
                .critical(critical)
                .build());
    }

    private void applyExtensionData(CertificateResponseDTO.CertificateResponseDTOBuilder builder, Certificate cert) {
        List<CertificateExtension> extensions = cert.getExtensions();
        if (extensions == null || extensions.isEmpty()) {
            return;
        }

        builder.SAN(extractSanFromExtensions(extensions));
        builder.includeSubjectKeyIdentifier(hasExtension(extensions, Extension.subjectKeyIdentifier.getId()));
        builder.includeAuthorityKeyIdentifier(hasExtension(extensions, Extension.authorityKeyIdentifier.getId()));
        builder.includeExtendedKeyUsage(hasExtension(extensions, Extension.extendedKeyUsage.getId()));
    }

    private void applyExtensionData(CertificateOverviewDTO.CertificateOverviewDTOBuilder builder, Certificate cert) {
        List<CertificateExtension> extensions = cert.getExtensions();
        if (extensions == null || extensions.isEmpty()) {
            return;
        }

        builder.SAN(extractSanFromExtensions(extensions));
        builder.includeSubjectKeyIdentifier(hasExtension(extensions, Extension.subjectKeyIdentifier.getId()));
        builder.includeAuthorityKeyIdentifier(hasExtension(extensions, Extension.authorityKeyIdentifier.getId()));
        builder.includeExtendedKeyUsage(hasExtension(extensions, Extension.extendedKeyUsage.getId()));
    }

    private boolean hasExtension(List<CertificateExtension> extensions, String oid) {
        for (CertificateExtension extension : extensions) {
            if (oid.equals(extension.getExtensionOid())) {
                return true;
            }
        }
        return false;
    }

    private String extractSanFromExtensions(List<CertificateExtension> extensions) {
        for (CertificateExtension extension : extensions) {
            if (Extension.subjectAlternativeName.getId().equals(extension.getExtensionOid())) {
                try {
                    return decodeSanValue(extension.getExtensionValue());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String decodeSanValue(String extensionValueBase64) throws Exception {
        if (extensionValueBase64 == null || extensionValueBase64.isBlank()) {
            return null;
        }

        byte[] decoded = Base64.getDecoder().decode(extensionValueBase64);
        ASN1OctetString octetString = ASN1OctetString.getInstance(decoded);
        ASN1Primitive primitive = ASN1Primitive.fromByteArray(octetString.getOctets());
        GeneralNames names = GeneralNames.getInstance(primitive);

        for (GeneralName name : names.getNames()) {
            if (name.getTagNo() == GeneralName.dNSName) {
                return name.getName().toString();
            }
        }

        return null;
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private User loadCurrentUser() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new Exception("Unauthenticated request.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new Exception("User not found."));
    }

    public X500Name buildSubjectName(CertificateRequestDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("CN=").append(dto.getCommonName());
        sb.append(", O=").append(dto.getOrganization());

        if (dto.getOrganizationalUnit() != null && !dto.getOrganizationalUnit().isBlank()) {
            sb.append(", OU=").append(dto.getOrganizationalUnit());
        }
        if (dto.getLocality() != null && !dto.getLocality().isBlank()) {
            sb.append(", L=").append(dto.getLocality());
        }
        if (dto.getCountry() != null && !dto.getCountry().isBlank()) {
            sb.append(", C=").append(dto.getCountry());
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            sb.append(", E=").append(dto.getEmail());
        }

        return new X500Name(sb.toString());
    }

    public BigInteger generateSerialNumber() {
        return BigInteger.valueOf(System.currentTimeMillis());
    }

    private void addStandardExtensions(JcaX509v3CertificateBuilder certBuilder, CertificateRequestDTO dto, java.security.PublicKey publicKey, java.security.PublicKey issuerPublicKey, String issuerSerialNumber) throws Exception {
        CertificateType type = dto.getType();

        // Always add BasicConstraints and KeyUsage (mandatory extensions)
        if (type == CertificateType.ROOT) {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else if (type == CertificateType.INTERMEDIATE) {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        }

        // Add Subject Alternative Name if provided
        if (dto.getSAN() != null && !dto.getSAN().isBlank()) {
            GeneralName altName = new GeneralName(GeneralName.dNSName, dto.getSAN());
            GeneralNames subjectAltNames = new GeneralNames(altName);
            certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
        }

        org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils extUtils = new org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils();

        // Add optional extensions based on user selection
        if (dto.isIncludeSubjectKeyIdentifier()) {
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(publicKey));
        }

        if (dto.isIncludeAuthorityKeyIdentifier() && (type == CertificateType.INTERMEDIATE || type == CertificateType.END_ENTITY)) {
            certBuilder.addExtension(Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerPublicKey));
        }

        if (dto.isIncludeExtendedKeyUsage() && type == CertificateType.END_ENTITY) {
            KeyPurposeId[] keyPurposeIds = new KeyPurposeId[] {
                    KeyPurposeId.id_kp_serverAuth,
                    KeyPurposeId.id_kp_clientAuth
            };
            ExtendedKeyUsage eku = new ExtendedKeyUsage(keyPurposeIds);
            certBuilder.addExtension(Extension.extendedKeyUsage, false, eku);
        }

        // CRL URL
        String crlUrl = "https://localhost:8443/api/certificates/crl/" + issuerSerialNumber + ".crl";
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, new DERIA5String(crlUrl));
        GeneralNames gns = new GeneralNames(gn);

        DistributionPointName dpn = new DistributionPointName(0, gns);
        DistributionPoint distp = new DistributionPoint(dpn, null, null);
        CRLDistPoint crlDistPoint = new CRLDistPoint(new DistributionPoint[]{distp});

        certBuilder.addExtension(Extension.cRLDistributionPoints, false, crlDistPoint);
    }

    public PrivateKey loadPrivateKey(Certificate issuerEntity, Organization organization) throws Exception {
        byte[] decryptedKey = certificateEncryptionService.decryptPrivateKey(
                issuerEntity.getPrivateKeyEncrypted(),
                organization);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decryptedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Retrieves all active ROOT and INTERMEDIATE certificates to be used as potential issuers,
     * while lazily filtering out and updating expired items.
     */
    public List<CaSuggestionResponseDTO> getActiveSigningAuthorities() {
        List<CertificateType> signingTypes = List.of(CertificateType.ROOT, CertificateType.INTERMEDIATE);

        List<Certificate> activeCas = certificateRepository.findByStatusAndTypeIn(
                CertificateStatus.ACTIVE,
                signingTypes
        );

        LocalDateTime now = LocalDateTime.now();

        return activeCas.stream()
                .filter(cert -> {
                    if (cert.getValidTo().isBefore(now)) {
                        cert.setStatus(CertificateStatus.EXPIRED);
                        certificateRepository.save(cert);
                        return false;
                    }
                    return true;
                })
                .map(cert -> CaSuggestionResponseDTO.builder()
                        .serialNumber(cert.getSerialNumber())
                        .commonName(cert.getCommonName())
                        .organizationName(cert.getOrganization() != null ? cert.getOrganization().getName() : "N/A")
                        .build())
                .toList();
    }

    public String extractCrlUrlFromExtensions(List<CertificateExtension> extensions) {
        for (CertificateExtension extension : extensions) {
            if (Extension.cRLDistributionPoints.getId().equals(extension.getExtensionOid())) {
                try {
                    if (extension.getExtensionValue() == null || extension.getExtensionValue().isBlank()) {
                        return null;
                    }

                    byte[] decoded = Base64.getDecoder().decode(extension.getExtensionValue());
                    ASN1OctetString octetString = ASN1OctetString.getInstance(decoded);
                    ASN1Primitive primitive = ASN1Primitive.fromByteArray(octetString.getOctets());
                    CRLDistPoint distPoint = CRLDistPoint.getInstance(primitive);

                    for (DistributionPoint dp : distPoint.getDistributionPoints()) {
                        DistributionPointName dpName = dp.getDistributionPoint();
                        if (dpName != null && dpName.getType() == DistributionPointName.FULL_NAME) {
                            GeneralNames generalNames = GeneralNames.getInstance(dpName.getName());
                            for (GeneralName name : generalNames.getNames()) {
                                if (name.getTagNo() == GeneralName.uniformResourceIdentifier) {
                                    return DERIA5String.getInstance(name.getName()).getString();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}
