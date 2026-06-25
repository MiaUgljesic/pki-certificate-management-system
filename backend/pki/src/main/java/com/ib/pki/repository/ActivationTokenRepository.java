package com.ib.pki.repository;

import com.ib.pki.model.ActivationToken;
import com.ib.pki.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findByToken(String token);

    Optional<ActivationToken> findByUser(User user);
}

