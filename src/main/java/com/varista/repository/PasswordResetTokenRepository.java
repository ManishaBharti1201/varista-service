package com.varista.repository;

import com.varista.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    void deleteByEmailIgnoreCase(String email);
}
