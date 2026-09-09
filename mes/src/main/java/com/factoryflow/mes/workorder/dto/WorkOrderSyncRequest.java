package com.factoryflow.mes.workorder.dto;
import com.factoryflow.mes.workorder.entity.SourceSystem;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import java.util.UUID;
//서로 별개의 애플리케이션이므로 MES에도 동일한 JSON 계약을 받는 DTO 생성 필요

public record WorkOrderSyncRequest(
        @NotNull SourceSystem sourceSystem,
        @NotNull UUID eventId,
        @NotBlank String eventType,

        @NotNull @Positive Long externalId,
        @NotBlank @Size(max = 255) String workOrderNo,
        @Positive int version,

        @NotBlank @Size(max = 255) String productCode,
        @Positive int plannedQuantity,
        @NotNull LocalDate dueDate,
        @PositiveOrZero int priority,

        @Size(max = 255) String routingCode,
        @Positive int routingRevision
) {
}
