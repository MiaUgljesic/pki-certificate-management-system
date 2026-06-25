package com.ib.pki.service;

import com.ib.pki.model.MasterKey;
import com.ib.pki.model.Organization;
import com.ib.pki.repository.MasterKeyRepository;
import com.ib.pki.repository.OrganizationKeyRepository;
import com.ib.pki.repository.OrganizationRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MasterKeyRotationIntegrationTest {

    @Autowired
    private MasterKeyService masterKeyService;

    @Autowired
    private MasterKeyRotationService masterKeyRotationService;

    @Autowired
    private OrganizationKeyService organizationKeyService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationKeyRepository organizationKeyRepository;

    @Autowired
    private MasterKeyRepository masterKeyRepository;

    @BeforeEach
    void setUp() throws Exception {
        organizationKeyRepository.deleteAll();
        masterKeyRepository.deleteAll();
        organizationRepository.deleteAll();

        masterKeyService.createNewMasterKey();

        organizationRepository.save(Organization.builder().name("OrgOne").build());
        organizationRepository.save(Organization.builder().name("OrgTwo").build());
        organizationRepository.save(Organization.builder().name("OrgThree").build());

        organizationRepository.findAll().forEach(organizationKeyService::createOrganizationKey);
    }

    @Test
    void shouldRotateMasterKeyTwiceAndKeepOrganizationKeysDecryptable() throws Exception {
        List<Organization> organizations = organizationRepository.findAll();
        assertOrganizationKeysDecryptable(organizations);

        for (int i = 0; i < 2; i++) {
            MasterKey activeMasterKey = masterKeyService.createNewMasterKey();
            runRotationUntilComplete(activeMasterKey);

            assertAllOrganizationKeysUseActiveMaster(activeMasterKey);
            assertOrganizationKeysDecryptable(organizations);
        }
    }

    private void runRotationUntilComplete(MasterKey activeMasterKey) {
        int guard = 0;
        while (hasOrganizationKeysUsingOldMaster(activeMasterKey) && guard < 50) {
            masterKeyRotationService.reencryptOrganizationKeysBatch();
            guard++;
        }

        Assertions.assertFalse(hasOrganizationKeysUsingOldMaster(activeMasterKey),
                "Old master keys remain after rotation");
    }

    private boolean hasOrganizationKeysUsingOldMaster(MasterKey activeMasterKey) {
        return organizationKeyRepository
                .findByActiveTrueAndMasterKeyNot(activeMasterKey, PageRequest.of(0, 1))
                .hasContent();
    }

    private void assertAllOrganizationKeysUseActiveMaster(MasterKey activeMasterKey) {
        organizationKeyRepository.findAll().forEach(organizationKey ->
                Assertions.assertEquals(activeMasterKey.getId(), organizationKey.getMasterKey().getId(),
                        "Organization key should reference the active master key"));
    }

    private void assertOrganizationKeysDecryptable(List<Organization> organizations) {
        for (Organization organization : organizations) {
            byte[] keyBytes = organizationKeyService.getOrganizationKeyBytes(organization);
            Assertions.assertNotNull(keyBytes);
            Assertions.assertTrue(keyBytes.length > 0);
        }
    }
}
