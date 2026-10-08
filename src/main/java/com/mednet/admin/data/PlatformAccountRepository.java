package com.mednet.admin.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccountEntity, String> {
    List<PlatformAccountEntity> findTop100ByOrderByCreatedAtDesc();

    List<PlatformAccountEntity> findByAccountTypeInAndStatus(List<String> accountTypes, String status);

    Optional<PlatformAccountEntity> findFirstByEmailIgnoreCase(String email);

    Optional<PlatformAccountEntity> findFirstByGoogleSubject(String googleSubject);

    Optional<PlatformAccountEntity> findFirstByVerificationTokenHash(String tokenHash);

    Optional<PlatformAccountEntity> findFirstByPasswordResetTokenHash(String tokenHash);

    Page<PlatformAccountEntity> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    Page<PlatformAccountEntity> findByStatus(String status, Pageable pageable);

    Page<PlatformAccountEntity> findByEmailContainingIgnoreCaseAndStatus(
            String email, String status, Pageable pageable);

    long countByStatus(String status);
}