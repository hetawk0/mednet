package com.mednet.notification.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface NotificationEmailOutboxRepository extends JpaRepository<NotificationEmailOutboxEntity, String> {

    boolean existsByNotificationId(String notificationId);

    Optional<NotificationEmailOutboxEntity> findByNotificationId(String notificationId);

    @Query("""
            select outbox.id from NotificationEmailOutboxEntity outbox
            where (outbox.status = 'PENDING' and outbox.nextAttemptAt <= :now)
               or (outbox.status = 'PROCESSING' and outbox.leaseExpiresAt <= :now)
            order by outbox.createdAt asc
            """)
    List<String> findReadyIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from NotificationEmailOutboxEntity outbox where outbox.id = :id")
    Optional<NotificationEmailOutboxEntity> findByIdForUpdate(@Param("id") String id);
}
