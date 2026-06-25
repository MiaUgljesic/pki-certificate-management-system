package com.ib.pki.repository;

import com.ib.pki.model.Organization;
import com.ib.pki.model.OrganizationKey;
import com.ib.pki.model.MasterKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationKeyRepository extends JpaRepository<OrganizationKey, Long> {
    Optional<OrganizationKey> findByOrganizationAndActiveTrue(Organization organization);

    Page<OrganizationKey> findByActiveTrueAndMasterKeyNot(MasterKey masterKey, Pageable pageable);
}
