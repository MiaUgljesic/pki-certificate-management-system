package com.ib.pki.service;

import com.ib.pki.PkiApplication;
import com.ib.pki.model.MasterKey;
import com.ib.pki.model.Organization;
import com.ib.pki.repository.MasterKeyRepository;
import com.ib.pki.repository.OrganizationKeyRepository;
import com.ib.pki.repository.OrganizationRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

class MasterKeyRotationRestartIntegrationTest {

    private static final String TEST_DB_URL =
            "jdbc:h2:mem:rotationdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void shouldResumeRotationAfterRestart() {
        MasterKey activeMasterKeyAfterRotation;

        try (ConfigurableApplicationContext context = startContext()) {
            SeedResult seed = seedData(context);
            activeMasterKeyAfterRotation = seed.activeMasterKeyAfterRotation;

            MasterKeyRotationService rotationService = context.getBean(MasterKeyRotationService.class);
            rotationService.reencryptOrganizationKeysBatch();

            Assertions.assertTrue(hasOrganizationKeysUsingOldMaster(context, activeMasterKeyAfterRotation),
                    "Expected remaining keys encrypted with old master key");
        }

        try (ConfigurableApplicationContext context = startContext()) {
            MasterKeyRepository masterKeyRepository = context.getBean(MasterKeyRepository.class);
            MasterKey activeMasterKey = masterKeyRepository.findByActiveTrue().orElseThrow();

            runRotationUntilComplete(context, activeMasterKey);

            Assertions.assertFalse(hasOrganizationKeysUsingOldMaster(context, activeMasterKey),
                    "Old master keys remain after restart");

            assertOrganizationKeysDecryptable(context);
        }
    }

    private ConfigurableApplicationContext startContext() {
        return new SpringApplicationBuilder(PkiApplication.class)
                .profiles("test")
            .run(
                "--spring.datasource.url=" + TEST_DB_URL,
                "--app.security.master-key-rotation-batch-delay-ms=600000",
                "--app.security.master-key-rotation-batch-size=1",
                "--app.security.master-key-rotation-check-delay-ms=600000",
                "--app.security.master-key-rotation-days=365");
    }

    private SeedResult seedData(ConfigurableApplicationContext context) {
        MasterKeyService masterKeyService = context.getBean(MasterKeyService.class);
        OrganizationRepository organizationRepository = context.getBean(OrganizationRepository.class);
        OrganizationKeyRepository organizationKeyRepository = context.getBean(OrganizationKeyRepository.class);
        MasterKeyRepository masterKeyRepository = context.getBean(MasterKeyRepository.class);
        OrganizationKeyService organizationKeyService = context.getBean(OrganizationKeyService.class);
        TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

        return transactionTemplate.execute(status -> {
            organizationKeyRepository.deleteAll();
            masterKeyRepository.deleteAll();
            organizationRepository.deleteAll();

            masterKeyService.createNewMasterKey();

            Organization orgOne = organizationRepository.save(Organization.builder().name("OrgOne").build());
            Organization orgTwo = organizationRepository.save(Organization.builder().name("OrgTwo").build());
            Organization orgThree = organizationRepository.save(Organization.builder().name("OrgThree").build());

            organizationKeyService.createOrganizationKey(orgOne);
            organizationKeyService.createOrganizationKey(orgTwo);
            organizationKeyService.createOrganizationKey(orgThree);

            MasterKey activeMasterKeyAfterRotation = masterKeyService.createNewMasterKey();
            return new SeedResult(activeMasterKeyAfterRotation);
        });
    }

    private void runRotationUntilComplete(ConfigurableApplicationContext context, MasterKey activeMasterKey) {
        MasterKeyRotationService rotationService = context.getBean(MasterKeyRotationService.class);
        int guard = 0;
        while (hasOrganizationKeysUsingOldMaster(context, activeMasterKey) && guard < 50) {
            rotationService.reencryptOrganizationKeysBatch();
            guard++;
        }
    }

    private boolean hasOrganizationKeysUsingOldMaster(ConfigurableApplicationContext context, MasterKey activeMasterKey) {
        OrganizationKeyRepository organizationKeyRepository = context.getBean(OrganizationKeyRepository.class);
        return organizationKeyRepository
                .findByActiveTrueAndMasterKeyNot(activeMasterKey, PageRequest.of(0, 1))
                .hasContent();
    }

    private void assertOrganizationKeysDecryptable(ConfigurableApplicationContext context) {
        OrganizationRepository organizationRepository = context.getBean(OrganizationRepository.class);
        OrganizationKeyService organizationKeyService = context.getBean(OrganizationKeyService.class);
        TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

        transactionTemplate.execute(status -> {
            List<Organization> organizations = organizationRepository.findAll();
            for (Organization organization : organizations) {
                byte[] keyBytes = organizationKeyService.getOrganizationKeyBytes(organization);
                Assertions.assertNotNull(keyBytes);
                Assertions.assertTrue(keyBytes.length > 0);
            }
            return null;
        });
    }

    private record SeedResult(MasterKey activeMasterKeyAfterRotation) {
    }
}
