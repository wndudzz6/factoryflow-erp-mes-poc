package com.factoryflow.erp.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "ERP 계획 버전. 계획 변경 시 증가하고 통신 재전송 시 유지") int version,

        String productCode,
        @Schema(description = "계획수량: 1 이상") int plannedQuantity,
        LocalDate dueDate,
        @Schema(description = "우선순위: 0 이상") int priority,

        String routingCode,
        @Schema(description = "라우팅 리비전: 1 이상") int routingRevision
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
