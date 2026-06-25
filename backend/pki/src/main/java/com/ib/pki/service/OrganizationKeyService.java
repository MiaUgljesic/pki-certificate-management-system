package com.ib.pki.service;

import com.ib.pki.model.MasterKey;
import com.ib.pki.model.Organization;
import com.ib.pki.model.OrganizationKey;
import com.ib.pki.repository.MasterKeyRepository;
import com.ib.pki.repository.OrganizationKeyRepository;
import com.ib.pki.util.CryptoUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrganizationKeyService {
    private final MasterKeyService masterKeyService;
    private final MasterKeyRepository masterKeyRepository;
    private final OrganizationKeyRepository organizationKeyRepository;

    public OrganizationKeyService(MasterKeyService masterKeyService, MasterKeyRepository masterKeyRepository, OrganizationKeyRepository organizationKeyRepository) {
        this.masterKeyService = masterKeyService;
        this.masterKeyRepository = masterKeyRepository;
        this.organizationKeyRepository = organizationKeyRepository;
    }

    // creates organization key when a new organization is created
    @Transactional
    public OrganizationKey createOrganizationKey(Organization organization) {
        try{
            // get current active master key from the database
            Optional<MasterKey> optionalMasterKey = masterKeyRepository.findByActiveTrue();
            if(optionalMasterKey.isEmpty()){
                throw new RuntimeException("No active master key found");
            }
            // extract master key
            MasterKey masterKey = optionalMasterKey.get();

            // decrypt the master key to use it for encrypting the organization key
            byte[] masterKeyBytes = masterKeyService.getActiveMasterKeyBytes();

            // generate a new random AES key for this organization
            byte[] rawOrganizationKey = CryptoUtil.generateAESKey();

            // encrypt the organization key using the master key
            String encryptedOrganizationKey = CryptoUtil.encrypt(rawOrganizationKey, masterKeyBytes);

            // create and save the organization key
            OrganizationKey organizationKey = OrganizationKey.builder().organization(organization)
                    .masterKey(masterKey).encryptedKeyData(encryptedOrganizationKey).active(true).build();
            return organizationKeyRepository.save(organizationKey);
        }catch (Exception e) {
            throw new RuntimeException("Failed to create organization key", e);
        }
    }

    // get decrypted organization key bytes for the given organization
    public byte[] getOrganizationKeyBytes(Organization organization) {
        // get active organization key for the given organization
        Optional<OrganizationKey> optionalOrganizationKey = organizationKeyRepository.findByOrganizationAndActiveTrue(organization);
        if(optionalOrganizationKey.isEmpty()){
            throw new RuntimeException("No active key for organization: " + organization.getName());
        }
        // extract organization key
        OrganizationKey organizationKey = optionalOrganizationKey.get();

        try{
            // use the specific master key version that is used for encryption of this organization key
            byte[] masterKeyBytes = masterKeyService.decryptMasterKey(organizationKey.getMasterKey());
            // return decrypted organization key
            return CryptoUtil.decrypt(organizationKey.getEncryptedKeyData(), masterKeyBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt organization key", e);
        }
    }
}
