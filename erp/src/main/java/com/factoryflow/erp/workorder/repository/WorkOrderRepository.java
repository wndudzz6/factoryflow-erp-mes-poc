package com.factoryflow.erp.workorder.repository;

import com.factoryflow.erp.workorder.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkOrderRepository
        extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);

    boolean existsByWorkOrderNo(String workOrderNo);
}