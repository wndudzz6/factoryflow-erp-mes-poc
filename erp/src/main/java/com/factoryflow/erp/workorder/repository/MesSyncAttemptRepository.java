package com.factoryflow.erp.workorder.repository;

import com.factoryflow.erp.workorder.entity.MesSyncAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MesSyncAttemptRepository extends JpaRepository<MesSyncAttempt, Long> {
    Page<MesSyncAttempt> findByWorkOrderId(Long workOrderId, Pageable pageable);
    Page<MesSyncAttempt> findByEventId(UUID eventId, Pageable pageable);
    boolean existsByEventId(UUID eventId);
}
