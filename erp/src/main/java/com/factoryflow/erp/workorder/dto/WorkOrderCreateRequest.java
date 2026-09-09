package com.factoryflow.erp.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record WorkOrderCreateRequest(
        @NotBlank @Size(max = 255) String workOrderNo,
        @NotBlank @Size(max = 255) String productCode,
        @Positive @Schema(description = "계획수량: 1 이상") int plannedQuantity,
        @NotNull LocalDate dueDate,
        @PositiveOrZero @Schema(description = "우선순위: 0 이상") int priority,
        @Size(max = 255) String routingCode,
        @Positive @Schema(description = "라우팅 리비전: 1 이상") int routingRevision
) {
}
