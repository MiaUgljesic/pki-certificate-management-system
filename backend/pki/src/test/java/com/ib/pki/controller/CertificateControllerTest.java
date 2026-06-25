package com.ib.pki.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ib.pki.model.Certificate;
import com.ib.pki.model.Organization;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.CertificateType;
import com.ib.pki.model.enums.UserRole;
import com.ib.pki.repository.CertificateRepository;
import com.ib.pki.repository.MasterKeyRepository;
import com.ib.pki.repository.OrganizationKeyRepository;
import com.ib.pki.repository.OrganizationRepository;
import com.ib.pki.repository.UserRepository;
import com.ib.pki.security.jwt.JwtService;
import com.ib.pki.service.MasterKeyService;
import com.ib.pki.service.OrganizationKeyService;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private OrganizationKeyRepository organizationKeyRepository;

    @Autowired
    private MasterKeyRepository masterKeyRepository;

    @Autowired
    private MasterKeyService masterKeyService;

    @Autowired
    private OrganizationKeyService organizationKeyService;

    @Autowired
    private PasswordEncoder passwordEncoder;

        @Autowired
        private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private Organization orgOne;
    private Organization orgTwo;
    private User adminUser;
    private User caUserOrgOne;
    private User caUserOrgTwo;

    @BeforeAll
    static void registerCryptoProvider() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        certificateRepository.deleteAll();
        organizationKeyRepository.deleteAll();
        masterKeyRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        masterKeyService.createNewMasterKey();

        orgOne = organizationRepository.save(Organization.builder().name("OrgOne").build());
        orgTwo = organizationRepository.save(Organization.builder().name("OrgTwo").build());

        organizationKeyService.createOrganizationKey(orgOne);
        organizationKeyService.createOrganizationKey(orgTwo);

        adminUser = userRepository.save(User.builder()
                .email("admin@example.com")
                .passwordHash(passwordEncoder.encode("AdminPass123!"))
                .firstName("Admin")
                .lastName("User")
                .role(UserRole.ADMIN)
                .active(true)
                .build());

        caUserOrgOne = userRepository.save(User.builder()
                .email("ca1@orgone.com")
                .passwordHash(passwordEncoder.encode("CaPass123!"))
                .firstName("CA")
                .lastName("OrgOne")
                .role(UserRole.CA_USER)
                .active(true)
                .organization(orgOne)
                .build());

        caUserOrgTwo = userRepository.save(User.builder()
                .email("ca2@orgtwo.com")
                .passwordHash(passwordEncoder.encode("CaPass123!"))
                .firstName("CA")
                .lastName("OrgTwo")
                .role(UserRole.CA_USER)
                .active(true)
                .organization(orgTwo)
                .build());

        Path keystorePath = Path.of("target/test-keystore.jks");
        Files.deleteIfExists(keystorePath);
    }

    @Test
    void issueRootCertificateAsAdmin() throws Exception {
        String token = jwtService.generateToken(adminUser);

        Map<String, Object> payload = new HashMap<>();
        payload.put("commonName", "OrgOne Root CA");
        payload.put("organization", "OrgOne");
                payload.put("SAN", "root.orgone.local");
        payload.put("validTo", new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
        payload.put("type", "ROOT");
        payload.put("includeSubjectKeyIdentifier", false);
        payload.put("includeAuthorityKeyIdentifier", false);
        payload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("successfully")));

        List<Certificate> certificates = certificateRepository.findAll();
        Certificate root = certificates.stream()
                .filter(cert -> cert.getType() == CertificateType.ROOT)
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertNotNull(root.getCertificateData());
        org.junit.jupiter.api.Assertions.assertFalse(root.getSerialNumber().isBlank());
    }

    @Test
    @Transactional
    void issueIntermediateAndEndEntityCertificates() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);

        Map<String, Object> rootPayload = new HashMap<>();
        rootPayload.put("commonName", "OrgOne Root CA");
        rootPayload.put("organization", "OrgOne");
                rootPayload.put("SAN", "root.orgone.local");
        rootPayload.put("validTo", new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
        rootPayload.put("type", "ROOT");
        rootPayload.put("includeSubjectKeyIdentifier", false);
        rootPayload.put("includeAuthorityKeyIdentifier", false);
        rootPayload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content(objectMapper.writeValueAsString(rootPayload)))
                .andExpect(status().isOk());

        Thread.sleep(2);

        Certificate root = certificateRepository.findAll().stream()
                .filter(cert -> cert.getType() == CertificateType.ROOT)
                .findFirst()
                .orElseThrow();

        String caToken = jwtService.generateToken(caUserOrgOne);
        Map<String, Object> intermediatePayload = new HashMap<>();
        intermediatePayload.put("commonName", "OrgOne Intermediate");
        intermediatePayload.put("organization", "OrgOne");
        intermediatePayload.put("SAN", "intermediate.orgone.local");
        intermediatePayload.put("validTo", new Date(System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000));
        intermediatePayload.put("type", "INTERMEDIATE");
        intermediatePayload.put("issuerSerialNumber", root.getSerialNumber());
        intermediatePayload.put("includeSubjectKeyIdentifier", false);
        intermediatePayload.put("includeAuthorityKeyIdentifier", false);
        intermediatePayload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + caToken)
                .content(objectMapper.writeValueAsString(intermediatePayload)))
                .andExpect(status().isOk());

        Thread.sleep(2);

        Certificate intermediate = certificateRepository.findAll().stream()
                .filter(cert -> cert.getType() == CertificateType.INTERMEDIATE)
                .findFirst()
                .orElseThrow();

        Map<String, Object> endEntityPayload = new HashMap<>();
        endEntityPayload.put("commonName", "OrgOne End Entity");
        endEntityPayload.put("organization", "OrgOne");
        endEntityPayload.put("SAN", "end-entity.orgone.local");
        endEntityPayload.put("validTo", new Date(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000));
        endEntityPayload.put("type", "END_ENTITY");
        endEntityPayload.put("issuerSerialNumber", intermediate.getSerialNumber());
        endEntityPayload.put("includeSubjectKeyIdentifier", true);
        endEntityPayload.put("includeAuthorityKeyIdentifier", true);
        endEntityPayload.put("includeExtendedKeyUsage", true);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + caToken)
                .content(objectMapper.writeValueAsString(endEntityPayload)))
                .andExpect(status().isOk());

        Certificate endEntity = certificateRepository.findAll().stream()
                .filter(cert -> cert.getType() == CertificateType.END_ENTITY)
                .findFirst()
                .orElseThrow();

        X509Certificate endEntityX509 = decodeCertificate(endEntity);
        org.junit.jupiter.api.Assertions.assertNotNull(endEntityX509.getExtensionValue("2.5.29.14"));
        System.out.println("Subject Key Identifier: " + Arrays.toString(endEntityX509.getExtensionValue("2.5.29.14")));
        org.junit.jupiter.api.Assertions.assertNotNull(endEntityX509.getExtensionValue("2.5.29.35"));
        org.junit.jupiter.api.Assertions.assertNotNull(endEntityX509.getExtensionValue("2.5.29.37"));
        org.junit.jupiter.api.Assertions.assertTrue(containsDnsSan(endEntityX509, "end-entity.orgone.local"));

        List<Map<String, Object>> extensionRows = jdbcTemplate.queryForList(
                "select extension_oid, extension_value, critical from certificate_extensions where certificate_id = ?",
                endEntity.getId());
        org.junit.jupiter.api.Assertions.assertEquals(4, extensionRows.size());

        Map<String, String> extensionValues = new HashMap<>();
        HashSet<String> criticalOids = new HashSet<>();
        for (Map<String, Object> row : extensionRows) {
            String oid = row.get("EXTENSION_OID").toString();
            String value = row.get("EXTENSION_VALUE").toString();
            System.out.println("Extension OID: " + oid + ", Value: " + value + ", Critical: " + row.get("CRITICAL"));
            extensionValues.put(oid, value);
            if (Boolean.TRUE.equals(row.get("CRITICAL"))) {
                criticalOids.add(oid);
            }
        }

        org.junit.jupiter.api.Assertions.assertTrue(extensionValues.containsKey(Extension.subjectAlternativeName.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(extensionValues.containsKey(Extension.subjectKeyIdentifier.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(extensionValues.containsKey(Extension.authorityKeyIdentifier.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(extensionValues.containsKey(Extension.extendedKeyUsage.getId()));

        org.junit.jupiter.api.Assertions.assertEquals(
                Base64.getEncoder().encodeToString(endEntityX509.getExtensionValue(Extension.subjectAlternativeName.getId())),
                extensionValues.get(Extension.subjectAlternativeName.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(
                Base64.getEncoder().encodeToString(endEntityX509.getExtensionValue(Extension.subjectKeyIdentifier.getId())),
                extensionValues.get(Extension.subjectKeyIdentifier.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(
                Base64.getEncoder().encodeToString(endEntityX509.getExtensionValue(Extension.authorityKeyIdentifier.getId())),
                extensionValues.get(Extension.authorityKeyIdentifier.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(
                Base64.getEncoder().encodeToString(endEntityX509.getExtensionValue(Extension.extendedKeyUsage.getId())),
                extensionValues.get(Extension.extendedKeyUsage.getId()));

        org.junit.jupiter.api.Assertions.assertEquals(
                "end-entity.orgone.local",
                decodeSanValue(extensionValues.get(Extension.subjectAlternativeName.getId())));

                String response = mockMvc.perform(get("/api/certificates/all")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                var responseJson = objectMapper.readTree(response);
                var content = responseJson.get("content");
                boolean matched = false;
                if (content != null && content.isArray()) {
                        for (var item : content) {
                                if (endEntity.getSerialNumber().equals(item.get("serialNumber").asText())) {
                                        matched = true;
                                        System.out.println("API Response SAN: " + item.get("SAN").asText());
                                        System.out.println("API Response includeSubjectKeyIdentifier: " + item.get("includeSubjectKeyIdentifier").asBoolean());
                                        System.out.println("API Response includeAuthorityKeyIdentifier: " + item.get("includeAuthorityKeyIdentifier").asBoolean());
                                        System.out.println("API Response includeExtendedKeyUsage: " + item.get("includeExtendedKeyUsage").asBoolean());
                                        org.junit.jupiter.api.Assertions.assertEquals("end-entity.orgone.local", item.get("SAN").asText());
                                        org.junit.jupiter.api.Assertions.assertTrue(item.get("includeSubjectKeyIdentifier").asBoolean());
                                        org.junit.jupiter.api.Assertions.assertTrue(item.get("includeAuthorityKeyIdentifier").asBoolean());
                                        org.junit.jupiter.api.Assertions.assertTrue(item.get("includeExtendedKeyUsage").asBoolean());
                                }
                        }
                }

                org.junit.jupiter.api.Assertions.assertTrue(matched);
    }

    @Test
    void issueCertificateShouldRejectOtherOrganizationIssuer() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);

        Map<String, Object> rootPayload = new HashMap<>();
        rootPayload.put("commonName", "OrgOne Root CA");
        rootPayload.put("organization", "OrgOne");
                rootPayload.put("SAN", "root.orgone.local");
        rootPayload.put("validTo", new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
        rootPayload.put("type", "ROOT");
        rootPayload.put("includeSubjectKeyIdentifier", false);
        rootPayload.put("includeAuthorityKeyIdentifier", false);
        rootPayload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content(objectMapper.writeValueAsString(rootPayload)))
                .andExpect(status().isOk());

        Certificate root = certificateRepository.findAll().stream()
                .filter(cert -> cert.getType() == CertificateType.ROOT)
                .findFirst()
                .orElseThrow();

        String otherCaToken = jwtService.generateToken(caUserOrgTwo);
        Map<String, Object> payload = new HashMap<>();
        payload.put("commonName", "OrgTwo End Entity");
        payload.put("organization", "OrgOne");
        payload.put("SAN", "end-entity.orgone.local");
        payload.put("validTo", new Date(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000));
        payload.put("type", "END_ENTITY");
        payload.put("issuerSerialNumber", root.getSerialNumber());
        payload.put("includeSubjectKeyIdentifier", false);
        payload.put("includeAuthorityKeyIdentifier", false);
        payload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + otherCaToken)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("organization")));
    }

    @Test
    void downloadCertificateShouldReturnPkcs12WithKeyAndCert() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);

        Map<String, Object> rootPayload = new HashMap<>();
        rootPayload.put("commonName", "OrgOne Root CA");
        rootPayload.put("organization", "OrgOne");
        rootPayload.put("SAN", "root.orgone.local");
        rootPayload.put("validTo", new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
        rootPayload.put("type", "ROOT");
        rootPayload.put("includeSubjectKeyIdentifier", false);
        rootPayload.put("includeAuthorityKeyIdentifier", false);
        rootPayload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + adminToken)
                .content(objectMapper.writeValueAsString(rootPayload)))
                .andExpect(status().isOk());

        Certificate root = certificateRepository.findAll().stream()
                .filter(cert -> cert.getType() == CertificateType.ROOT)
                .findFirst()
                .orElseThrow();

        String caToken = jwtService.generateToken(caUserOrgOne);
        Map<String, Object> intermediatePayload = new HashMap<>();
        intermediatePayload.put("commonName", "OrgOne Intermediate");
        intermediatePayload.put("organization", "OrgOne");
        intermediatePayload.put("SAN", "intermediate.orgone.local");
        intermediatePayload.put("validTo", new Date(System.currentTimeMillis() + 180L * 24 * 60 * 60 * 1000));
        intermediatePayload.put("type", "INTERMEDIATE");
        intermediatePayload.put("issuerSerialNumber", root.getSerialNumber());
        intermediatePayload.put("includeSubjectKeyIdentifier", false);
        intermediatePayload.put("includeAuthorityKeyIdentifier", false);
        intermediatePayload.put("includeExtendedKeyUsage", false);

        mockMvc.perform(post("/api/certificates/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + caToken)
                .content(objectMapper.writeValueAsString(intermediatePayload)))
                .andExpect(status().isOk());

        Certificate intermediate = certificateRepository.findAll().stream()
                .filter(cert -> cert.getType() == CertificateType.INTERMEDIATE)
                .findFirst()
                .orElseThrow();

        Map<String, Object> downloadPayload = new HashMap<>();
        downloadPayload.put("serialNumber", intermediate.getSerialNumber());
        downloadPayload.put("keyStorePassword", "TestPass123!");
        downloadPayload.put("alias", "download-alias");

        byte[] pkcs12Bytes = mockMvc.perform(post("/api/certificates/download")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + caToken)
                .content(objectMapper.writeValueAsString(downloadPayload)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-pkcs12"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("cert_" + intermediate.getSerialNumber() + ".p12")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(pkcs12Bytes), "TestPass123!".toCharArray());

        org.junit.jupiter.api.Assertions.assertTrue(keyStore.containsAlias("download-alias"));

        Key key = keyStore.getKey("download-alias", "TestPass123!".toCharArray());
        org.junit.jupiter.api.Assertions.assertNotNull(key);
        org.junit.jupiter.api.Assertions.assertTrue(key instanceof PrivateKey);

        X509Certificate storedCert = (X509Certificate) keyStore.getCertificate("download-alias");
        org.junit.jupiter.api.Assertions.assertNotNull(storedCert);
        org.junit.jupiter.api.Assertions.assertEquals(intermediate.getSerialNumber(), storedCert.getSerialNumber().toString());
    }

        private X509Certificate decodeCertificate(Certificate certificate) throws Exception {
                byte[] decoded = Base64.getDecoder().decode(certificate.getCertificateData());
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
        }

        private boolean containsDnsSan(X509Certificate certificate, String expectedDns) throws Exception {
                Collection<List<?>> altNames = certificate.getSubjectAlternativeNames();
                if (altNames == null) {
                        return false;
                }

                for (List<?> entry : altNames) {
                        if (entry.size() >= 2 && Integer.valueOf(2).equals(entry.get(0)) && expectedDns.equals(entry.get(1))) {
                                return true;
                        }
                }

                return false;
        }

        private String decodeSanValue(String extensionValueBase64) throws Exception {
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
}
