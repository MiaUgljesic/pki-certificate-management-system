package com.ib.pki.repository;

import com.ib.pki.model.RecoverToken;
import com.ib.pki.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RecoverTokenRepository extends JpaRepository<RecoverToken, Long> {
    Optional<RecoverToken> findByToken(String token);
}