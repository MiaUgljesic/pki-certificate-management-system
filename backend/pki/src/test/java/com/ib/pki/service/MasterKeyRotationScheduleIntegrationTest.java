package com.ib.pki.service;

import com.ib.pki.model.MasterKey;
import com.ib.pki.repository.MasterKeyRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(properties = {
        "app.security.master-key-rotation-days=1",
        "app.security.master-key-rotation-check-delay-ms=600000"
})
@ActiveProfiles("test")
@Transactional
class MasterKeyRotationScheduleIntegrationTest {

    @Autowired
    private MasterKeyService masterKeyService;

    @Autowired
    private MasterKeyRotationService masterKeyRotationService;

    @Autowired
    private MasterKeyRepository masterKeyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() throws Exception {
        masterKeyRepository.deleteAll();
        masterKeyService.createNewMasterKey();
    }

    @Test
    void shouldRotateWhenActiveKeyIsOlderThanConfiguredDays() {
        MasterKey activeKey = masterKeyRepository.findByActiveTrue().orElseThrow();
        LocalDateTime oldCreatedAt = LocalDateTime.now().minusDays(2);

        jdbcTemplate.update(
                "UPDATE master_keys SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(oldCreatedAt),
                activeKey.getId());

        entityManager.clear();

        masterKeyRotationService.rotateMasterKeyIfExpired();

        List<MasterKey> allKeys = masterKeyRepository.findAll();
        Assertions.assertEquals(2, allKeys.size(), "Expected a new master key to be created");

        MasterKey newActiveKey = masterKeyRepository.findByActiveTrue().orElseThrow();
        Assertions.assertNotEquals(activeKey.getId(), newActiveKey.getId());
        Assertions.assertTrue(newActiveKey.getVersion() > activeKey.getVersion());
    }
}
