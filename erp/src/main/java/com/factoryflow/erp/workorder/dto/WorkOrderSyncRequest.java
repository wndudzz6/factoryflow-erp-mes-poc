package com.factoryflow.erp.workorder.dto;

import com.factoryflow.erp.workorder.entity.SourceSystem;
import com.factoryflow.erp.workorder.entity.WorkOrder;

import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderSyncRequest(
        SourceSystem sourceSystem,
        UUID eventId,
        String eventType,

        Long externalId,
        String workOrderNo,
        int version,

        String productCode,
        int plannedQuantity,
        LocalDate dueDate,
        int priority,

        String routingCode,
        int routingRevision
) {

    public static WorkOrderSyncRequest from(WorkOrder workOrder) {
        return new WorkOrderSyncRequest(
                SourceSystem.ERP,
                UUID.randomUUID(),
                workOrder.getVersion() == 1 ? "WORK_ORDER_CREATED" : "WORK_ORDER_UPDATED",

                workOrder.getId(),
                workOrder.getWorkOrderNo(),
                workOrder.getVersion(),

                workOrder.getProductCode(),
                workOrder.getPlannedQuantity(),
                workOrder.getDueDate(),
                workOrder.getPriority(),

                workOrder.getRoutingCode(),
                workOrder.getRoutingRevision()
        );
    }
}
