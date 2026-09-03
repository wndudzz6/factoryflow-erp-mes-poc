package com.factoryflow.mes.workorder.repository;

import com.factoryflow.mes.workorder.entity.SourceSystem;
import com.factoryflow.mes.workorder.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkOrderRepository
        extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);

    Optional<WorkOrder> findBySourceSystemAndExternalId(
            SourceSystem sourceSystem,
            Long externalId
    );
}