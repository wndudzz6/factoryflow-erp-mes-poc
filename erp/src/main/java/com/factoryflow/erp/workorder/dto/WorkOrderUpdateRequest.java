package com.factoryflow.erp.workorder.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record WorkOrderUpdateRequest(
        @Positive int plannedQuantity,
        @NotNull LocalDate dueDate,
        @PositiveOrZero int priority,
        @Size(max = 255) String routingCode,
        @Positive int routingRevision
) {
}
