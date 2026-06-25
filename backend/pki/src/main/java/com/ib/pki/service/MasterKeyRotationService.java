package com.ib.pki.service;

import com.ib.pki.config.AppProperties;
import com.ib.pki.model.MasterKey;
import com.ib.pki.model.OrganizationKey;
import com.ib.pki.repository.MasterKeyRepository;
import com.ib.pki.repository.OrganizationKeyRepository;
import com.ib.pki.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MasterKeyRotationService {
    private final MasterKeyRepository masterKeyRepository;
    private final OrganizationKeyRepository organizationKeyRepository;
    private final MasterKeyService masterKeyService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.security.master-key-rotation-batch-delay-ms}")
    @Transactional
    public void reencryptOrganizationKeysBatch() {
        Optional<MasterKey> optionalActiveKey = masterKeyRepository.findByActiveTrue();
        if (optionalActiveKey.isEmpty()) {
            return;
        }

        MasterKey activeMasterKey = optionalActiveKey.get();
        int batchSize = appProperties.getMasterKeyRotationBatchSize();
        Page<OrganizationKey> batch = organizationKeyRepository
                .findByActiveTrueAndMasterKeyNot(activeMasterKey, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }

        byte[] activeMasterKeyBytes = masterKeyService.decryptMasterKey(activeMasterKey);
        Map<Long, byte[]> decryptedOldKeysCache = new HashMap<>();

        for (OrganizationKey organizationKey : batch.getContent()) {
            try {
                Long oldMasterKeyId = organizationKey.getMasterKey().getId();

                byte[] oldMasterKeyBytes = decryptedOldKeysCache.computeIfAbsent(oldMasterKeyId,
                        id -> masterKeyService.decryptMasterKey(organizationKey.getMasterKey())
                );

                byte[] rawOrganizationKey = CryptoUtil.decrypt(organizationKey.getEncryptedKeyData(), oldMasterKeyBytes);
                String reencrypted = CryptoUtil.encrypt(rawOrganizationKey, activeMasterKeyBytes);
                organizationKey.setEncryptedKeyData(reencrypted);
                organizationKey.setMasterKey(activeMasterKey);
            } catch (Exception e) {
                throw new RuntimeException("Failed to re-encrypt organization key: " + organizationKey.getId(), e);
            }
        }

        organizationKeyRepository.saveAll(batch.getContent());
    }

    @Scheduled(fixedDelayString = "${app.security.master-key-rotation-check-delay-ms}")
    @Transactional
    public void rotateMasterKeyIfExpired() {
        Optional<MasterKey> optionalActiveKey = masterKeyRepository.findByActiveTrue();
        if (optionalActiveKey.isEmpty()) {
            return;
        }

        MasterKey activeMasterKey = optionalActiveKey.get();
        if (activeMasterKey.getCreatedAt() == null) {
            return;
        }

        int rotationDays = appProperties.getMasterKeyRotationDays();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(rotationDays);
        if (activeMasterKey.getCreatedAt().isBefore(cutoff)) {
            masterKeyService.createNewMasterKey();
        }
    }
}
