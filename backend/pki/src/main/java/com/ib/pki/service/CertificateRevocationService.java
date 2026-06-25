package com.ib.pki.service;

import com.ib.pki.dto.response.certificate.CertificateResponseDTO;
import com.ib.pki.dto.response.certificate.CertificateStatusCheckResponseDTO;
import com.ib.pki.dto.response.certificate.CrlItemResponseDTO;
import com.ib.pki.model.Certificate;
import com.ib.pki.model.User;
import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.model.enums.CertificateType;
import com.ib.pki.model.enums.RevocationReason;
import com.ib.pki.model.enums.UserRole;
import com.ib.pki.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ib.pki.dto.response.certificate.CertificateStatusCheckResponseDTO;
import com.ib.pki.model.Certificate;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CRLEntryHolder;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import javax.net.ssl.*;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Transactional
public class CertificateRevocationService {

    private final CertificateRepository certificateRepository;
    private final CertificateEncryptionService certificateEncryptionService;
    private final CertificateService certificateService;

    /**
     * Revokes a specific certificate, cascading the revocation downward,
     * and returns the updated main certificate entity.
     */
    public CertificateResponseDTO revokeCertificate(String serialNumber, RevocationReason reason, User loggedInUser) {
        Certificate cert = certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Certificate with serial number " + serialNumber + " was not found."));

        if (cert.getStatus() == CertificateStatus.REVOKED) {
            throw new IllegalStateException("This certificate has already been revoked.");
        }

        if (cert.getValidTo().isBefore(LocalDateTime.now())) {
            if (cert.getStatus() != CertificateStatus.EXPIRED) {
                cert.setStatus(CertificateStatus.EXPIRED);
                certificateRepository.save(cert);
            }
            throw new IllegalStateException("Cannot revoke an expired certificate.");
        }

        boolean isAdmin = loggedInUser.getRole() == UserRole.ADMIN;
        boolean isCaUser = loggedInUser.getRole() == UserRole.CA_USER;

        if (!isAdmin) {
            if (isCaUser) {
                if (!cert.getOrganization().getId().equals(loggedInUser.getOrganization().getId())) {
                    throw new AccessDeniedException("You can only revoke certificates within your organization.");
                }
            } else {
                if (cert.getOwner() == null || !cert.getOwner().getId().equals(loggedInUser.getId())) {
                    throw new AccessDeniedException("You can only revoke your own certificates.");
                }
            }
        }

        // Execute revocation on the target certificate
        executeRevocation(cert, reason);

        if (cert.getType() == CertificateType.ROOT || cert.getType() == CertificateType.INTERMEDIATE) {
            revokeChildCertificates(cert, reason);
        }
        CertificateResponseDTO dto = certificateService.mapToDTO(cert);
        return dto;
    }

    /**
     * Unrevokes a certificate that was placed on temporary suspension (CERTIFICATE_HOLD).
     */
    public void unrevokeCertificate(String serialNumber, User loggedInUser) {
        Certificate cert = certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Certificate with serial number " + serialNumber + " was not found."));

        if (cert.getValidTo().isBefore(LocalDateTime.now())) {
            if (cert.getStatus() == CertificateStatus.ACTIVE) {
                cert.setStatus(CertificateStatus.EXPIRED);
                certificateRepository.save(cert);
            }
            throw new IllegalStateException("Cannot unrevoke an expired certificate.");
        }

        if (cert.getStatus() != CertificateStatus.REVOKED || cert.getRevocationReason() != RevocationReason.CERTIFICATE_HOLD) {
            throw new IllegalStateException("Only certificates with an active 'CERTIFICATE_HOLD' status can be unrevoked.");
        }

        boolean isAdmin = loggedInUser.getRole() == UserRole.ADMIN;
        boolean isCaUser = loggedInUser.getRole() == UserRole.CA_USER;

        if (!isAdmin) {
            if (isCaUser) {
                if (!cert.getOrganization().getId().equals(loggedInUser.getOrganization().getId())) {
                    throw new AccessDeniedException("You can only reinstate certificates within your organization.");
                }
            } else {
                if (cert.getOwner() == null || !cert.getOwner().getId().equals(loggedInUser.getId())) {
                    throw new AccessDeniedException("You can only reinstate your own certificates.");
                }
            }
        }

        executeUnrevocation(cert);

        if (cert.getType() == CertificateType.ROOT || cert.getType() == CertificateType.INTERMEDIATE) {
            unrevokeChildCertificates(cert);
        }
    }

    /**
     * Recursively traverses down the certificate authority tree to revoke all subordinate items.
     */
    private void revokeChildCertificates(Certificate parentCert, RevocationReason reason) {
        List<Certificate> children = certificateRepository.findByIssuer(parentCert);

        for (Certificate child : children) {
            if (child.getValidTo().isBefore(LocalDateTime.now())) {
                if (child.getStatus() == CertificateStatus.ACTIVE) {
                    child.setStatus(CertificateStatus.EXPIRED);
                    certificateRepository.save(child);
                }
                continue;
            }
            if (child.getStatus() == CertificateStatus.ACTIVE) {
                // Inherit critical threat contexts down to child entities if they possess signing authorities
                RevocationReason childReason = ((reason == RevocationReason.KEY_COMPROMISE || reason == RevocationReason.CA_COMPROMISE)
                        && child.getType() != CertificateType.END_ENTITY)
                        ? RevocationReason.CA_COMPROMISE : reason;

                executeRevocation(child, childReason);

                if (child.getType() == CertificateType.ROOT || child.getType() == CertificateType.INTERMEDIATE) {
                    revokeChildCertificates(child, childReason);
                }
            }
        }
    }

    /**
     * Recursively reinstates nested certificates that were temporarily suspended due to a parent HOLD.
     */
    private void unrevokeChildCertificates(Certificate parentCert) {
        List<Certificate> children = certificateRepository.findByIssuer(parentCert);

        for (Certificate child : children) {
            if (child.getValidTo().isBefore(LocalDateTime.now())) {
                if (child.getStatus() == CertificateStatus.ACTIVE) {
                    child.setStatus(CertificateStatus.EXPIRED);
                    certificateRepository.save(child);
                }
                continue;
            }
            // Re-activate only if the child was implicitly suspended under the parent's hold context
            if (child.getStatus() == CertificateStatus.REVOKED && child.getRevocationReason() == RevocationReason.CERTIFICATE_HOLD) {
                executeUnrevocation(child);

                if (child.getType() == CertificateType.ROOT || child.getType() == CertificateType.INTERMEDIATE) {
                    unrevokeChildCertificates(child);
                }
            }
        }
    }

    public List<CrlItemResponseDTO> getCertificateRevocationList() {
        List<Certificate> revokedCertificates = certificateRepository.findByStatus(CertificateStatus.REVOKED);

        return revokedCertificates.stream()
                .map(cert -> CrlItemResponseDTO.builder()
                        .serialNumber(cert.getSerialNumber())
                        .reason(cert.getRevocationReason() != null ? cert.getRevocationReason().toString() : "UNSPECIFIED")
                        .revokedAt(cert.getRevokedAt() != null ? cert.getRevokedAt().toString() : LocalDateTime.now().toString())
                        .build())
                .collect(Collectors.toList());
    }

    private void executeRevocation(Certificate cert, RevocationReason reason) {
        cert.setStatus(CertificateStatus.REVOKED);
        cert.setRevokedAt(LocalDateTime.now());
        cert.setRevocationReason(reason);
        certificateRepository.save(cert);
    }

    private void executeUnrevocation(Certificate cert) {
        cert.setStatus(CertificateStatus.ACTIVE);
        cert.setRevokedAt(null);
        cert.setRevocationReason(null);
        certificateRepository.save(cert);
    }

    /**
     * Generates a fully signed, RFC 5280 compliant X.509 Certificate Revocation List (CRL) for a given CA.
     */
    public byte[] generateX509Crl(String caSerialNumber) {
        try {
            Certificate caCertEntity = certificateRepository.findBySerialNumber(caSerialNumber)
                    .orElseThrow(() -> new IllegalArgumentException("CA certificate not found in database."));

            if (caCertEntity.getType() == CertificateType.END_ENTITY) {
                throw new IllegalArgumentException("End-entity certificates cannot issue a CRL.");
            }

            byte[] decodedCertBytes = Base64.getDecoder().decode(caCertEntity.getCertificateData());
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate caHolder = (X509Certificate) certFactory.generateCertificate(
                    new ByteArrayInputStream(decodedCertBytes)
            );

            byte[] decryptedKey = certificateEncryptionService.decryptPrivateKey(
                    caCertEntity.getPrivateKeyEncrypted(),
                    caCertEntity.getOrganization()
            );

            String algorithm = (caCertEntity.getKeyAlgorithm() != null && !caCertEntity.getKeyAlgorithm().isBlank())
                    ? caCertEntity.getKeyAlgorithm()
                    : "RSA";

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decryptedKey);
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
            PrivateKey caPrivateKey = keyFactory.generatePrivate(keySpec);

            Date thisUpdate = new Date();
            Date nextUpdate = Date.from(LocalDateTime.now().plusDays(7)
                    .atZone(ZoneId.systemDefault()).toInstant());

            JcaX509v2CRLBuilder crlBuilder = new JcaX509v2CRLBuilder(
                    caHolder.getSubjectX500Principal(),
                    thisUpdate
            );
            crlBuilder.setNextUpdate(nextUpdate);

            BigInteger crlNumber = BigInteger.valueOf(System.currentTimeMillis());
            crlBuilder.addExtension(Extension.cRLNumber, false, new CRLNumber(crlNumber));

            List<Certificate> revokedSubordinates = new ArrayList<>(
                    certificateRepository.findByIssuer(caCertEntity).stream()
                            .filter(cert -> cert.getStatus() == CertificateStatus.REVOKED)
                            .toList()
            );

            if (caCertEntity.getType() == CertificateType.ROOT && caCertEntity.getStatus() == CertificateStatus.REVOKED) {
                revokedSubordinates.add(caCertEntity);
            }
            for (Certificate revokedCert : revokedSubordinates) {
                BigInteger serial = new BigInteger(revokedCert.getSerialNumber().trim());

                Date revocationDate = thisUpdate;
                if (revokedCert.getRevokedAt() != null) {
                    revocationDate = Date.from(revokedCert.getRevokedAt()
                            .atZone(ZoneId.systemDefault()).toInstant());
                }

                int reasonCode = mapToBouncyCastleReason(revokedCert.getRevocationReason());

                ExtensionsGenerator extGen = new ExtensionsGenerator();
                extGen.addExtension(Extension.reasonCode, false, CRLReason.lookup(reasonCode));

                crlBuilder.addCRLEntry(serial, revocationDate, extGen.generate());
            }

            String sigAlg = (caCertEntity.getSignatureAlgorithm() != null && !caCertEntity.getSignatureAlgorithm().isBlank())
                    ? caCertEntity.getSignatureAlgorithm()
                    : "SHA256withRSA";

            if (java.security.Security.getProvider("BC") == null) {
                java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            }

            ContentSigner signer = new JcaContentSignerBuilder(sigAlg)
                    .setProvider("BC")
                    .build(caPrivateKey);

            X509CRLHolder crlHolder = crlBuilder.build(signer);

            return crlHolder.getEncoded();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to generate X.509 CRL: " + e.getMessage(), e);
        }
    }

    private int mapToBouncyCastleReason(RevocationReason reason) {
        if (reason == null) return CRLReason.unspecified;
        return switch (reason) {
            case KEY_COMPROMISE -> CRLReason.keyCompromise;
            case CA_COMPROMISE -> CRLReason.cACompromise;
            case AFFILIATION_CHANGED -> CRLReason.affiliationChanged;
            case SUPERSEDED -> CRLReason.superseded;
            case CESSATION_OF_OPERATION -> CRLReason.cessationOfOperation;
            case CERTIFICATE_HOLD -> CRLReason.certificateHold;
            case REMOVE_FROM_CRL -> CRLReason.removeFromCRL;
            case PRIVILEGE_WITHDRAWN -> CRLReason.privilegeWithdrawn;
            case AA_COMPROMISE -> CRLReason.aACompromise;
            default -> CRLReason.unspecified;
        };
    }
    public CertificateStatusCheckResponseDTO verifyCertificateInCrl(String serialNumber) {

        Certificate certEntity = certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Certificate with serial number " + serialNumber + " does not exist."));

        String crlDistributionPoint = certificateService.extractCrlUrlFromExtensions(certEntity.getExtensions());

        if (crlDistributionPoint == null || crlDistributionPoint.isBlank()) {
            if (certEntity.getType() == com.ib.pki.model.enums.CertificateType.ROOT) {
                crlDistributionPoint = "https://localhost:8443/api/certificates/crl/" + certEntity.getSerialNumber() + ".crl";
            } else if (certEntity.getIssuer() != null) {
                crlDistributionPoint = "https://localhost:8443/api/certificates/crl/" + certEntity.getIssuer().getSerialNumber() + ".crl";
            } else {
                throw new IllegalStateException("No CRL Distrubition Points Extension.");
            }
        }

        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            URL url = new URL(crlDistributionPoint);
            try (InputStream in = url.openStream()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
                X509CRL crl = (X509CRL) cf.generateCRL(in);

                BigInteger targetSerial = new BigInteger(serialNumber.trim());
                X509CRLEntry revokedEntry = crl.getRevokedCertificate(targetSerial);

                boolean isRevoked = (revokedEntry != null);
                String reason = "VALID";

                if (isRevoked) {
                    reason = (revokedEntry.getRevocationReason() != null)
                            ? revokedEntry.getRevocationReason().name()
                            : "UNKNOWN_REASON";
                }

                return CertificateStatusCheckResponseDTO.builder()
                        .serialNumber(serialNumber)
                        .crlUrl(crlDistributionPoint)
                        .isRevoked(isRevoked)
                        .reason(reason)
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during validation: " + e.getMessage());
        }
    }
}