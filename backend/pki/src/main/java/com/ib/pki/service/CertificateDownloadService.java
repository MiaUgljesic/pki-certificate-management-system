package com.ib.pki.service;

import com.ib.pki.model.Certificate;
import com.ib.pki.model.enums.CertificateFormat;
import com.ib.pki.repository.CertificateRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Service
public class CertificateDownloadService {

    private final CertificateRepository certificateRepository;

    public CertificateDownloadService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    public byte[] certificateDownload(String serialNumber, CertificateFormat format) {
        Optional<Certificate> optionalCertificate = certificateRepository.findBySerialNumber(serialNumber);
        if (optionalCertificate.isEmpty()) {
            throw new RuntimeException(("Certificate with serial number " + serialNumber + " not found."));
        }
        Certificate certificate = optionalCertificate.get();

        if (format.equals(CertificateFormat.PEM)) {
            return downloadAsPem(certificate.getCertificateData());
        } else {
            return downloadAsCer(certificate.getCertificateData());
        }
    }

    private byte[] downloadAsCer(String certificateData) {
        return toDer(certificateData);
    }

    private byte[] downloadAsPem(String certificateData) {
        return toPem(certificateData);
    }

    public byte[] toDer(String base64Data) {
        return Base64.getDecoder().decode(base64Data);
    }

    public byte[] toPem(String base64Data) {
        StringBuilder pem = new StringBuilder();
        String pemHeader = "-----BEGIN CERTIFICATE-----\n";
        String pemFooter = "-----END CERTIFICATE-----\n";
        pem.append(pemHeader);
        for (int i = 0; i < base64Data.length(); i += 64) {
            pem.append(base64Data, i, Math.min(i + 64, base64Data.length()));
            pem.append('\n');
        }
        pem.append(pemFooter);
        return pem.toString().getBytes(StandardCharsets.UTF_8);
    }
}