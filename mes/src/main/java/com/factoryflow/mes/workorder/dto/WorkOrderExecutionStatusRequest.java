package com.factoryflow.mes.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import jakarta.validation.constraints.NotNull;

public record WorkOrderExecutionStatusRequest(@NotNull WorkOrderExecutionStatus executionStatus) {
}
