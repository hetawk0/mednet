package com.mednet.admin.data;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEventEntity, String> {
    List<AdminAuditEventEntity> findTop100ByOrderByCreatedAtDesc();
}