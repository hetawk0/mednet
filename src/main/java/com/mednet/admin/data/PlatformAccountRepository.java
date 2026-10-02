package com.mednet.admin.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccountEntity, String> {
    List<PlatformAccountEntity> findTop100ByOrderByCreatedAtDesc();

    Optional<PlatformAccountEntity> findFirstByEmailIgnoreCase(String email);

    Optional<PlatformAccountEntity> findFirstByGoogleSubject(String googleSubject);

    long countByStatus(String status);
}