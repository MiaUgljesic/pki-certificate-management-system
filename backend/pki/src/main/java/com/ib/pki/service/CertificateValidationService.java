package com.ib.pki.service;

import com.ib.pki.model.Certificate;
import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CertificateValidationService {

    private final CertificateRepository certificateRepository;

    /**
     * Check if the status is anything other than 'ACTIVE'.
     */
    public void checkNotActiveStatus(X509Certificate certificate) throws Exception {
        String serialNumber = certificate.getSerialNumber().toString();
        Optional<Certificate> certEntity = certificateRepository.findBySerialNumber(serialNumber);

        if (certEntity.isPresent() && certEntity.get().getStatus() != CertificateStatus.ACTIVE) {
            throw new Exception("The issuer certificate is no longer ACTIVE and cannot be used for signing.");
        }
    }

    /**
     * Verifies that the child certificate's expiration date does not exceed the issuer's expiration date.
     */
    public void verifyValidityPeriod(X509Certificate issuerCert, Date requestedTo) throws Exception {
        if (requestedTo.after(issuerCert.getNotAfter())) {
            throw new Exception("The requested expiration date exceeds the issuer's validity period.");
        }
    }

    /**
     * Comprehensive validation logic for an issuer certificate.
     * This method must be called before issuing any non-root certificate.
     */
    public void validateIssuerChain(Certificate issuerEntity, Date requestedTo) throws Exception {
        X509Certificate issuerCert = convertToX509Certificate(issuerEntity.getCertificateData());

        // Ensure the new certificate stays within the issuer's time bounds
        verifyValidityPeriod(issuerCert, requestedTo);

        // Check if the issuer certificate is currently expired
        issuerCert.checkValidity(new Date());


        // Basic Constraints Check: Ensure the issuer is actually a CA
        if (issuerCert.getBasicConstraints() == -1) {
            throw new Exception("The selected certificate does not have CA authority (Basic Constraints check failed).");
        }

        // Check the database for ACTIVE status
        checkNotActiveStatus(issuerCert);

        // Digital Signature Verification: Ensure the certificate integrity
        try {
            if (issuerEntity.getIssuer() != null) {
                X509Certificate parent = convertToX509Certificate(issuerEntity.getIssuer().getCertificateData());
                issuerCert.verify(parent.getPublicKey());
            } else {
                issuerCert.verify(issuerCert.getPublicKey());
            }
        } catch (Exception e) {
            throw new Exception("Issuer signature verification failed.");
        }

        if (issuerEntity.getIssuer() != null) {
            validateIssuerChain(issuerEntity.getIssuer(), requestedTo);
        }

    }

    private X509Certificate convertToX509Certificate(String certificateData) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(certificateData);
        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
    }
}