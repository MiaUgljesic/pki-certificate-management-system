package com.ib.pki.service;

import com.ib.pki.config.AppProperties;
import com.ib.pki.model.MasterKey;
import com.ib.pki.repository.MasterKeyRepository;
import com.ib.pki.util.CryptoUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class MasterKeyService {
    private final MasterKeyRepository masterKeyRepository;
    private final AppProperties appProperties;

    public MasterKeyService(MasterKeyRepository masterKeyRepository, AppProperties appProperties) {
        this.masterKeyRepository = masterKeyRepository;
        this.appProperties = appProperties;
    }

    // on app startup, check if an active master key exists - if not, create one using the configured secret
    @PostConstruct
    @Transactional
    public void initializeMasterKey(){
        Optional<MasterKey> optionalMasterKey = masterKeyRepository.findByActiveTrue();
        if(optionalMasterKey.isEmpty()){
            createNewMasterKey();
        }
    }

    // creates new master key
    @Transactional
    public MasterKey createNewMasterKey(){
        try{
            // deactivate any currently active master keys
            List<MasterKey> activeKeys = masterKeyRepository.findAllByActiveTrue();
            if (!activeKeys.isEmpty()) {
                LocalDateTime rotatedAt = LocalDateTime.now();
                for (MasterKey activeKey : activeKeys) {
                    activeKey.setActive(false);
                    activeKey.setRotatedAt(rotatedAt);
                }
                masterKeyRepository.saveAll(activeKeys);
            }

            // generate random salt
            byte[] salt = CryptoUtil.generateSalt();

            // derive key from secret and salt
            byte[] derivedKey = CryptoUtil.deriveKeyFromSecret(appProperties.getMasterKeySecret(), salt);

            // generate master key and encrypt it with the derived key
            byte[] rawMasterKey = CryptoUtil.generateAESKey();
            String encryptedMasterKey = CryptoUtil.encrypt(rawMasterKey, derivedKey);

            // calculate next version of master key
            int nextVersion = masterKeyRepository.findMaxVersion().orElse(0) + 1;

            // create and save master key
            MasterKey masterKey = MasterKey.builder().version(nextVersion).encryptedKeyData(encryptedMasterKey)
                    .salt(Base64.getEncoder().encodeToString(salt)).active(true).build();
            return masterKeyRepository.save(masterKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create master key", e);
        }
    }

    // returns the decrypted active master key
    public byte[] getActiveMasterKeyBytes() {
        // get current active master key from the database
        Optional<MasterKey> optionalMasterKey = masterKeyRepository.findByActiveTrue();
        if(optionalMasterKey.isEmpty()){
            throw new RuntimeException("No active master key found");
        }

        // extract master key
        MasterKey masterKey = optionalMasterKey.get();

        // decrypt master key and return the raw AES bytes
        return decryptMasterKey(masterKey);
    }

    // decrypts an encrypted master key and returns the raw AES key bytes
    public byte[] decryptMasterKey(MasterKey masterKey) {
        try {
            // decode the stored salt
            byte[] salt = Base64.getDecoder().decode(masterKey.getSalt());

            // recreate the same derived encryption key used during encryption
            byte[] derivedKey = CryptoUtil.deriveKeyFromSecret(appProperties.getMasterKeySecret(), salt);

            // decrypt the encrypted master key using the derived key and return the raw AES key bytes
            return CryptoUtil.decrypt(masterKey.getEncryptedKeyData(), derivedKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
