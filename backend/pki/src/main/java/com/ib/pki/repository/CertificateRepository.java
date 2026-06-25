package com.ib.pki.repository;

import com.ib.pki.model.enums.CertificateStatus;
import com.ib.pki.model.enums.CertificateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ib.pki.model.Certificate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import java.time.LocalDateTime;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findBySerialNumber(String serialNumber);

    @Query("SELECT c FROM Certificate c WHERE c.owner.id = :userId AND c.type = 'END_ENTITY'")
    Page<Certificate> findUserCertificatesWithAllRelations(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM Certificate c WHERE c.owner.id = :userId AND c.type = 'END_ENTITY' AND c.validFrom >= :startOfDay AND c.validFrom <= :endOfDay")
    Page<Certificate> findUserCertificatesWithAllRelationsAndDate(@Param("userId") Long userId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay, Pageable pageable);

    @Query("SELECT c FROM Certificate c WHERE c.validFrom >= :startOfDay AND c.validFrom <= :endOfDay")
    Page<Certificate> findCertificatesWithDateRange(@Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay, Pageable pageable);

    @Query("SELECT c FROM Certificate c WHERE c.organization.id = :organizationId")
    Page<Certificate> findCertificatesByOrganizationId(@Param("organizationId") Long organizationId, Pageable pageable);

    @Query("SELECT c FROM Certificate c WHERE c.organization.id = :organizationId AND c.validFrom >= :startOfDay AND c.validFrom <= :endOfDay")
    Page<Certificate> findCertificatesByOrganizationIdAndDate(@Param("organizationId") Long organizationId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay, Pageable pageable);

    List<Certificate> findByIssuer(Certificate parentCert);

    List<Certificate> findByStatus(CertificateStatus certificateStatus);

    List<Certificate> findByTypeInAndStatus(List<CertificateType> types, CertificateStatus status);

    List<Certificate> findByStatusAndTypeIn(CertificateStatus status, List<CertificateType> types);

    boolean existsByCommonNameAndOrganizationNameAndOrganizationalUnitAndCountryAndEmailAndIssuer(String commonName, String organizationName, String organizationalUnit, String country, String email, Certificate issuer);
}
