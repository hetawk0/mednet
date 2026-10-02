package com.mednet.admin.data;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequestEntity, String> {
    List<ServiceRequestEntity> findTop100ByOrderByCreatedAtDesc();

    List<ServiceRequestEntity> findTop100ByStatusOrderByCreatedAtDesc(String status);

    long countByStatusIn(Collection<String> statuses);
}