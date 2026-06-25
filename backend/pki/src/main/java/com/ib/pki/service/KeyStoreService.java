package com.ib.pki.service;

import com.ib.pki.dto.request.certificate.CertificateDownloadRequest;
import com.ib.pki.model.Certificate;
import com.ib.pki.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class KeyStoreService {

    @Value("${app.keystore.path}")
    private String keyStorePath;

    @Value("${app.keystore.password}")
    private String keyStorePassword;

    private final CertificateRepository certificateRepository;
    private final CertificateEncryptionService certificateEncryptionService;

    /**
     * Requirement: Dynamic Configuration.
     * Loads the KeyStore password and path from environment variables (via .env).
     */
    public void saveCertificate(X509Certificate cert, PrivateKey privateKey) {
        try {
            char[] passwordArray = keyStorePassword.toCharArray();
            KeyStore keyStore = KeyStore.getInstance("JKS");

            File file = new File(keyStorePath);
            if (!file.exists()) {
                keyStore.load(null, passwordArray);
            } else {
                try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                    keyStore.load(fis, passwordArray);
                }
            }

            String alias = cert.getSerialNumber().toString();
            keyStore.setKeyEntry(alias, privateKey, passwordArray, new java.security.cert.Certificate[]{cert});

            try (FileOutputStream fos = new FileOutputStream(keyStorePath)) {
                keyStore.store(fos, passwordArray);
            }
        } catch (Exception e) {
            throw new RuntimeException("KeyStore operation failed. Check your .env configuration.", e);
        }
    }

    /**
     * Retrieval for Validation.
     * Fetches the X509 certificate from the file so Student 1 can validate it.
     */
    public X509Certificate getCertificate(String serialNumber) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            char[] passwordArray = keyStorePassword.toCharArray();

            // Use try-with-resources to ensure the stream is closed automatically
            try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                keyStore.load(fis, passwordArray);
            }

            X509Certificate cert = (X509Certificate) keyStore.getCertificate(serialNumber);

            if (cert == null) {
                throw new RuntimeException("Certificate with serial number " + serialNumber + " not found in KeyStore.");
            }

            return cert;
        } catch (Exception e) {
            // Logging the actual error helps with debugging
            throw new RuntimeException("Failed to load certificate from KeyStore: " + e.getMessage(), e);
        }
    }

    /**
     * Generiše PKCS12 keystore u memoriji i vraća ga kao niz bajtova.
     */
    public byte[] generateDownloadableKeyStore(CertificateDownloadRequest request) {
        try {
            Certificate certEntity = certificateRepository.findBySerialNumber(request.getSerialNumber())
                    .orElseThrow(() -> new RuntimeException("Certificate not found for serial number: " + request.getSerialNumber()));

            X509Certificate cert = convertToX509Certificate(certEntity.getCertificateData());
            PrivateKey privateKey = getPrivateKeyForCertificate(certEntity);

            if (privateKey == null) {
                throw new RuntimeException("Privatni ključ nije dostupan za preuzimanje.");
            }

            // 2. Kreiranje PKCS12 KeyStore-a u memoriji
            KeyStore pkcs12KeyStore = KeyStore.getInstance("PKCS12");
            pkcs12KeyStore.load(null, null); // Inicijalizacija praznog keystore-a

            // Postavljanje aliasa (ako korisnik nije uneo, koristi serijski broj)
            String alias = (request.getAlias() != null && !request.getAlias().isBlank())
                    ? request.getAlias()
                    : request.getSerialNumber();

            char[] userPassword = request.getKeyStorePassword().toCharArray();

            // Pakovanje ključa i sertifikata (Tačka 2 vašeg zahteva)
            pkcs12KeyStore.setKeyEntry(alias, privateKey, userPassword, new java.security.cert.Certificate[]{cert});

            // 3. Zapisivanje u ByteArrayOutputStream umesto na disk
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                pkcs12KeyStore.store(baos, userPassword);
                return baos.toByteArray();
            }

        } catch (Exception e) {
            throw new RuntimeException("Greška prilikom generisanja KeyStore fajla za preuzimanje: " + e.getMessage(), e);
        }
    }

    // Pomoćne metode (prilagodi svojoj arhitekturi)
    private X509Certificate getCertificateFromServerKeyStore(String serialNumber) {
        // Tvoja postojeća getCertificate metoda
        return this.getCertificate(serialNumber);
    }

    private X509Certificate convertToX509Certificate(String certificateData) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(certificateData);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(decoded));
    }

    private PrivateKey getPrivateKeyForCertificate(Certificate certEntity) throws Exception {
        if (certEntity.getPrivateKeyEncrypted() == null || certEntity.getPrivateKeyEncrypted().isBlank()) {
            return null;
        }
        byte[] decryptedKey = certificateEncryptionService.decryptPrivateKey(
                certEntity.getPrivateKeyEncrypted(),
                certEntity.getOrganization());

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decryptedKey);
        String algorithm = (certEntity.getKeyAlgorithm() != null && !certEntity.getKeyAlgorithm().isBlank())
                ? certEntity.getKeyAlgorithm()
                : "RSA";
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
        return keyFactory.generatePrivate(keySpec);
    }
}