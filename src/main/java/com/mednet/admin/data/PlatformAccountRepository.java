package com.mednet.admin.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccountEntity, String> {
    List<PlatformAccountEntity> findTop100ByOrderByCreatedAtDesc();

    List<PlatformAccountEntity> findByAccountTypeInAndStatus(List<String> accountTypes, String status);

    Optional<PlatformAccountEntity> findFirstByEmailIgnoreCase(String email);

    Optional<PlatformAccountEntity> findFirstByGoogleSubject(String googleSubject);

    Optional<PlatformAccountEntity> findFirstByVerificationTokenHash(String tokenHash);

    Optional<PlatformAccountEntity> findFirstByPasswordResetTokenHash(String tokenHash);

    @Query("""
            SELECT account FROM PlatformAccountEntity account
            WHERE (:search = ''
                OR LOWER(account.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(account.displayName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(account.id) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR account.status = :status)
              AND (:accountType IS NULL OR account.accountType = :accountType)
              AND (:emailVerified IS NULL OR account.emailVerified = :emailVerified)
            """)
    Page<PlatformAccountEntity> searchAccounts(
            @Param("search") String search,
            @Param("status") String status,
            @Param("accountType") String accountType,
            @Param("emailVerified") Boolean emailVerified,
            Pageable pageable);

    long countByStatus(String status);
}