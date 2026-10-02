package com.mednet.admin.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderApplicationRepository extends JpaRepository<ProviderApplicationEntity, String> {
    List<ProviderApplicationEntity> findTop100ByOrderByCreatedAtDesc();

    List<ProviderApplicationEntity> findTop100ByStatusOrderByCreatedAtDesc(String status);

    Optional<ProviderApplicationEntity> findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
            String email, String status);

    long countByStatus(String status);
}