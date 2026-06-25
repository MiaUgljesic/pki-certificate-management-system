package com.ib.pki.service;
import com.ib.pki.dto.request.csr.AutogenerateCertificateSigningRequest;
import com.ib.pki.dto.response.csr.AutogenerateCertificateSigningResponse;
import com.ib.pki.dto.response.csr.CaDTO;
import com.ib.pki.dto.response.csr.CertificateSigningRequest;
import com.ib.pki.dto.response.csr.OrganizationDTO;
import com.ib.pki.model.Certificate;
import com.ib.pki.model.Organization;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.model.enums.CertificateType;
import com.ib.pki.repository.CertificateRepository;
import com.ib.pki.repository.OrganizationRepository;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.openssl.PEMParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class CsrService {
    private final CertificateRepository certificateRepository;
    private final CertificateValidationService validationService;
    private final CertificateService certificateService;
    private final OrganizationRepository organizationRepository;

    public CsrService(CertificateRepository certificateRepository, CertificateValidationService validationService, CertificateService certificateService, KeyStoreService keyStoreService, OrganizationRepository organizationRepository) {
        this.certificateRepository = certificateRepository;
        this.validationService = validationService;
        this.certificateService = certificateService;
        this.organizationRepository = organizationRepository;
    }

    public void issueCertificateFromCsr(byte[] csrBytes, String issuerSerialNumber, Date validTo, User owner) throws Exception {
        // parse csr bytes
        if (csrBytes == null || csrBytes.length == 0) {
            throw new RuntimeException("CSR bytes cannot be null or empty");
        }
        CertificateSigningRequest csr = parseCsr(csrBytes);

        // load issuer by serial number
        if (issuerSerialNumber == null || issuerSerialNumber.isBlank()) {
            throw new RuntimeException("Issuer serial number is required");
        }
        Optional<Certificate> optionalIssuerCertificate = certificateRepository.findBySerialNumber(issuerSerialNumber);
        if (optionalIssuerCertificate.isEmpty()) {
            throw new RuntimeException("Issuer with serial number " + issuerSerialNumber + " not found");
        }
        Certificate issuerCertificate = optionalIssuerCertificate.get();

        //load organization
        if (issuerCertificate.getOrganization() == null) {
            throw new RuntimeException("Issuer certificate must be associated with an organization");
        }
        Organization organization = issuerCertificate.getOrganization();

        // validate issuer chain
        validationService.validateIssuerChain(issuerCertificate, validTo);

        // check for duplicated certificate
        checkForDuplicate(csr, issuerCertificate);

        // convert certificate into X509 format
        X509Certificate issuerX509 = certificateService.convertToX509Certificate(issuerCertificate.getCertificateData());

        // build name
        X500Name issuerName = new X500Name(issuerX509.getSubjectX500Principal().getName());
        X500Name subject = buildSubjectName(csr);

        // build certificate
        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(issuerName, certificateService.generateSerialNumber(),
                new Date(), validTo, subject, csr.getPublicKey());

        // add basic extensions
        certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certificateBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        // sign certificate
        PrivateKey issuerPrivateKey = certificateService.loadPrivateKey(issuerCertificate, organization);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(issuerPrivateKey);
        X509Certificate signedCertificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateBuilder.build(signer));

        // save
        saveCsrCertificateToDatabase(signedCertificate, csr, issuerCertificate, owner);
    }

    private void saveCsrCertificateToDatabase(X509Certificate signedCertificate, CertificateSigningRequest csr, Certificate issuerCertificate, User owner) throws Exception {

        String encodedCert = Base64.getEncoder().encodeToString(signedCertificate.getEncoded());

        Certificate certificate = Certificate.builder().serialNumber(signedCertificate.getSerialNumber().toString())
                .commonName(csr.getCommonName()).organizationName(csr.getOrganization())
                .organizationalUnit(csr.getOrganizationalUnit()).country(csr.getCountry())
                .email(csr.getEmail()).type(CertificateType.END_ENTITY)
                .status(CertificateStatus.ACTIVE).validFrom(LocalDateTime.now())
                .validTo(signedCertificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .certificateData(encodedCert).privateKeyEncrypted(null).owner(owner)
                .organization(issuerCertificate.getOrganization()).issuer(issuerCertificate)
                .keyAlgorithm("RSA").keySize(2048).signatureAlgorithm("SHA256WithRSAEncryption").build();

        certificate.setExtensions(certificateService.buildExtensionsFromCertificate(signedCertificate, certificate));
        certificateRepository.save(certificate);
    }

    public CertificateSigningRequest parseCsr(byte[] csrBytes) {
        try {
            // use pemParser to parse CSR
            PEMParser pemParser = new PEMParser(new StringReader(new String(csrBytes)));

            // cast CSR into standard format
            PKCS10CertificationRequest csr = (PKCS10CertificationRequest) pemParser.readObject();

            // extract public key from CSR
            PublicKey publicKey = new JcaPEMKeyConverter().getPublicKey(csr.getSubjectPublicKeyInfo());

            // extract subject from CSR
            X500Name subject = csr.getSubject();

            // extract common name, organization, organizational unit, country and email from subject
            String cn = getAttribute(subject, BCStyle.CN);
            String o = getAttribute(subject, BCStyle.O);
            String ou = getAttribute(subject, BCStyle.OU);
            String c = getAttribute(subject, BCStyle.C);
            String e = getAttribute(subject, BCStyle.E);

            return CertificateSigningRequest.builder().commonName(cn).organization(o).organizationalUnit(ou)
                    .country(c).email(e).publicKey(publicKey).build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSR: " + e.getMessage());
        }
    }

    // helper method to build X500Name from CSR
    private X500Name buildSubjectName(CertificateSigningRequest csr) {
        StringBuilder sb = new StringBuilder();
        sb.append("CN=").append(csr.getCommonName());
        sb.append(", O=").append(csr.getOrganization());

        if (csr.getOrganizationalUnit() != null && !csr.getOrganizationalUnit().isBlank()) {
            sb.append(", OU=").append(csr.getOrganizationalUnit());
        }
        if (csr.getCountry() != null && !csr.getCountry().isBlank()) {
            sb.append(", C=").append(csr.getCountry());
        }
        if (csr.getEmail() != null && !csr.getEmail().isBlank()) {
            sb.append(", E=").append(csr.getEmail());
        }

        return new X500Name(sb.toString());
    }

    // helper method to extract one attribute from X500Name
    public static String getAttribute(X500Name x500Name, ASN1ObjectIdentifier attributeStyle) {
        RDN[] rdns = x500Name.getRDNs(attributeStyle);
        if (rdns != null && rdns.length > 0) {
            // extract attribute value
            return IETFUtils.valueToString(rdns[0].getFirst().getValue());
        }
        return null;

    }

    public AutogenerateCertificateSigningResponse autogenerateCertificate (AutogenerateCertificateSigningRequest request, User owner) throws Exception {
        // load issuer by serial number
        if (request.getIssuerSerialNumber() == null || request.getIssuerSerialNumber().isBlank()) {
            throw new RuntimeException("Issuer serial number is required");
        }
        Optional<Certificate> optionalIssuerCertificate = certificateRepository.findBySerialNumber(request.getIssuerSerialNumber());
        if (optionalIssuerCertificate.isEmpty()) {
            throw new RuntimeException("Issuer with serial number " + request.getIssuerSerialNumber() + " not found");
        }
        Certificate issuerCertificate = optionalIssuerCertificate.get();

        //load organization
        if (issuerCertificate.getOrganization() == null) {
            throw new RuntimeException("Issuer certificate must be associated with an organization");
        }
        Organization organization = issuerCertificate.getOrganization();

        // validate issuer chain
        validationService.validateIssuerChain(issuerCertificate, request.getValidTo());

        // generate keypair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // convert certificate into X509 format
        X509Certificate issuerX509 = certificateService.convertToX509Certificate(issuerCertificate.getCertificateData());

        // build name
        X500Name issuerName = new X500Name(issuerX509.getSubjectX500Principal().getName());
        CertificateSigningRequest csr = mapToCertificateRequestDTO(request, keyPair.getPublic());
        X500Name subject = buildSubjectName(csr);

        // check for duplicated certificate
        checkForDuplicate(csr, issuerCertificate);

        // build certificate
        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(issuerName, certificateService.generateSerialNumber(),
                new Date(), request.getValidTo(), subject, csr.getPublicKey());

        // add basic extensions
        certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certificateBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        if (request.getIncludeSubjectKeyIdentifier()) {
            SubjectPublicKeyInfo pkInfo = SubjectPublicKeyInfo.getInstance(ASN1Primitive.fromByteArray(csr.getPublicKey().getEncoded()));
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(pkInfo.getPublicKeyData().getBytes());
            certificateBuilder.addExtension(Extension.subjectKeyIdentifier, false, new SubjectKeyIdentifier(hash));
        }

        if (request.getIncludeAuthorityKeyIdentifier()) {
            SubjectPublicKeyInfo issuerPkInfo = SubjectPublicKeyInfo.getInstance(ASN1Primitive.fromByteArray(issuerX509.getPublicKey().getEncoded()));
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(issuerPkInfo.getPublicKeyData().getBytes());
            certificateBuilder.addExtension(Extension.authorityKeyIdentifier, false, new AuthorityKeyIdentifier(hash));
        }

        if (request.getIncludeExtendedKeyUsage()) {
            KeyPurposeId[] kpIds = { KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth };
            certificateBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(kpIds));
        }

        // sign certificate
        PrivateKey issuerPrivateKey = certificateService.loadPrivateKey(issuerCertificate, organization);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(issuerPrivateKey);
        X509Certificate signedCertificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateBuilder.build(signer));

        // save without private key
        saveCsrCertificateToDatabase(signedCertificate, csr, issuerCertificate, owner);

        // generate PKCS12 keystore in memory for download
        String alias = (request.getAlias() != null && !request.getAlias().isBlank()) ? request.getAlias() : signedCertificate.getSerialNumber().toString();
        char[] password = request.getKeyStorePassword().toCharArray();

        KeyStore pkcs12KeyStore = KeyStore.getInstance("PKCS12");
        pkcs12KeyStore.load(null, null);
        pkcs12KeyStore.setKeyEntry(alias, keyPair.getPrivate(), password, new java.security.cert.Certificate[]{signedCertificate});

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            pkcs12KeyStore.store(baos, password);
            return AutogenerateCertificateSigningResponse.builder().certificateBytes(baos.toByteArray()).serialNumber(signedCertificate.getSerialNumber().toString()).build();
        }
    }

    // helper method to map autogenerate request to certificate request dto
    private CertificateSigningRequest mapToCertificateRequestDTO(AutogenerateCertificateSigningRequest request, PublicKey publicKey) {
        return CertificateSigningRequest.builder().commonName(request.getCommonName())
                .organization(request.getOrganization()).organizationalUnit(request.getOrganizationalUnit())
                .country(request.getCountry()).email(request.getEmail()).publicKey(publicKey).build();
    }

    public List<CaDTO> getAvailableIssuers() {
        List<CaDTO> caDTOS = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        List<Certificate> caCertificates =  certificateRepository.findByTypeInAndStatus(List.of(CertificateType.ROOT, CertificateType.INTERMEDIATE),CertificateStatus.ACTIVE);
        for (Certificate certificate : caCertificates){
            if(certificate.getValidTo().isAfter(now)){
                CaDTO caDTO = CaDTO.builder().serialNumber(certificate.getSerialNumber())
                        .commonName(certificate.getCommonName()).validTo(certificate.getValidTo()).build();
                caDTOS.add(caDTO);
            }
        }
        return caDTOS;
    }

    public List<OrganizationDTO> getOrganizations() {
        List<OrganizationDTO> organizationDTOS = new ArrayList<>();
        List<Organization> organizations = organizationRepository.findAll();
        for(Organization organization : organizations){
            OrganizationDTO organizationDTO = OrganizationDTO.builder().id(organization.getId()).name(organization.getName()).build();
            organizationDTOS.add(organizationDTO);
        }
        return organizationDTOS;
    }

    // helper method to check if same certificate already exists
    private void checkForDuplicate(CertificateSigningRequest csr, Certificate issuerCertificate) {
        boolean exists = certificateRepository
                .existsByCommonNameAndOrganizationNameAndOrganizationalUnitAndCountryAndEmailAndIssuer(
                        csr.getCommonName(), csr.getOrganization(),
                        csr.getOrganizationalUnit(), csr.getCountry(),
                        csr.getEmail(), issuerCertificate);
        if (exists) {
            throw new RuntimeException("Certificate with the same subject and issuer already exists");
        }
    }
}