package com.factoryflow.mes.workorder.dto;
import com.factoryflow.mes.workorder.entity.SourceSystem;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import java.util.UUID;
//서로 별개의 애플리케이션이므로 MES에도 동일한 JSON 계약을 받는 DTO 생성 필요

public record WorkOrderSyncRequest(
        @NotNull SourceSystem sourceSystem,
        @NotNull @Schema(description = "ERP 전송 추적 ID. 동일 ID의 반복 수신도 기록하며 version으로 반영 여부 판정") UUID eventId,
        @NotBlank String eventType,

        @NotNull @Positive Long externalId,
        @NotBlank @Size(max = 255) String workOrderNo,
        @Positive @Schema(description = "ERP 계획 버전. 계획 변경 시 증가하고 통신 재전송 시 유지") int version,

        @NotBlank @Size(max = 255) String productCode,
        @Positive @Schema(description = "계획수량: 1 이상") int plannedQuantity,
        @NotNull LocalDate dueDate,
        @PositiveOrZero @Schema(description = "우선순위: 0 이상") int priority,

        @Size(max = 255) String routingCode,
        @Positive @Schema(description = "라우팅 리비전: 1 이상") int routingRevision
) {
}
