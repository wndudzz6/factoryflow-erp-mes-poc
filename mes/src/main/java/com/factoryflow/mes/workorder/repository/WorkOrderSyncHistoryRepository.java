package com.factoryflow.mes.workorder.repository;

import com.factoryflow.mes.workorder.entity.SourceSystem;
import com.factoryflow.mes.workorder.entity.WorkOrderSyncHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkOrderSyncHistoryRepository extends JpaRepository<WorkOrderSyncHistory, Long> {
    Page<WorkOrderSyncHistory> findBySourceSystemAndExternalId(SourceSystem sourceSystem,
            Long externalId, Pageable pageable);
    Page<WorkOrderSyncHistory> findByEventId(UUID eventId, Pageable pageable);
    boolean existsByEventId(UUID eventId);
}
