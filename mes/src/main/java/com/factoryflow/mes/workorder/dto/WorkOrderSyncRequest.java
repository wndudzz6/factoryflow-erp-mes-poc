package com.factoryflow.mes.workorder.dto;
import com.factoryflow.mes.workorder.entity.SourceSystem;

import java.time.LocalDate;
import java.util.UUID;
//서로 별개의 애플리케이션이므로 MES에도 동일한 JSON 계약을 받는 DTO 생성 필요

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
}