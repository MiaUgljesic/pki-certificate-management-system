package com.ib.pki.service;

import com.ib.pki.model.Organization;
import com.ib.pki.util.CryptoUtil;
import org.springframework.stereotype.Service;

@Service
public class CertificateEncryptionService {

    private final OrganizationKeyService organizationKeyService;

    public CertificateEncryptionService(OrganizationKeyService organizationKeyService) {
        this.organizationKeyService = organizationKeyService;
    }

    // called before saving a certificate's private key to database
    public String encryptPrivateKey(byte[] privateKey, Organization organization){
        try{
            // get organization key
            byte[] organizationKey = organizationKeyService.getOrganizationKeyBytes(organization);
            // return encrypted private key using organization key
            return CryptoUtil.encrypt(privateKey, organizationKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt private key", e);
        }
    }

    // called when the private key is needed (download, signing etc.)
    public byte[] decryptPrivateKey(String  encryptedPrivateKey, Organization organization){
        try{
            // get organization key
            byte[] organizationKey = organizationKeyService.getOrganizationKeyBytes(organization);
            // return decrypted private key using organization key
            return CryptoUtil.decrypt(encryptedPrivateKey, organizationKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt private key", e);
        }
    }
}