package com.ib.pki.repository;

import com.ib.pki.model.Organization;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByName(@NotBlank(message = "Organization is required") String organization);
}
