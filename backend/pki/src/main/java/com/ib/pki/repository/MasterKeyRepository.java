package com.ib.pki.repository;

import com.ib.pki.model.MasterKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MasterKeyRepository extends JpaRepository<MasterKey, Long> {
    Optional<MasterKey> findByActiveTrue();

    List<MasterKey> findAllByActiveTrue();

    @Query("SELECT MAX(m.version) FROM MasterKey m")
    Optional<Integer> findMaxVersion();
}
