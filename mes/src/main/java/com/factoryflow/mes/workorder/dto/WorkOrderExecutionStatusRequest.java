package com.factoryflow.mes.workorder.dto;

import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import jakarta.validation.constraints.NotNull;

public record WorkOrderExecutionStatusRequest(@NotNull WorkOrderExecutionStatus executionStatus) {
}
