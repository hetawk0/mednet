package com.mednet.provider.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderApplicationRepository extends JpaRepository<ProviderApplicationEntity, String> {
    List<ProviderApplicationEntity> findTop100ByOrderByCreatedAtDesc();

    List<ProviderApplicationEntity> findTop100ByStatusOrderByCreatedAtDesc(String status);

    Optional<ProviderApplicationEntity> findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
            String email, String status);

    Optional<ProviderApplicationEntity> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    @Query("""
            select provider from ProviderApplicationEntity provider
            where provider.status = 'APPROVED'
              and (:specialty = '' or lower(provider.specialty) like lower(concat('%', :specialty, '%')))
              and (:search = '' or lower(provider.displayName) like lower(concat('%', :search, '%'))
                   or lower(provider.specialty) like lower(concat('%', :search, '%')))
            """)
    Page<ProviderApplicationEntity> findApprovedDirectory(
            @Param("specialty") String specialty,
            @Param("search") String search,
            Pageable pageable);

    long countByStatus(String status);
}