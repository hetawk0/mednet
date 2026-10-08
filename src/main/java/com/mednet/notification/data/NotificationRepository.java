package com.mednet.notification.data;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
    Page<NotificationEntity> findByRecipientAccountIdOrderByCreatedAtDesc(
            String recipientAccountId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndRecipientAccountId(String id, String recipientAccountId);

    boolean existsByEventKey(String eventKey);

    long countByRecipientAccountIdAndReadAtIsNull(String recipientAccountId);
}
