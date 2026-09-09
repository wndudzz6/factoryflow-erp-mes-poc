package com.factoryflow.mes.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;

public record WorkOrderExecutionStatusResponse(Long id, String workOrderNo,
                                               WorkOrderExecutionStatus executionStatus,
                                               @Schema(description = "MES가 마지막으로 적용한 ERP 계획 버전") int sourceVersion) {
    public static WorkOrderExecutionStatusResponse from(WorkOrder workOrder) {
        return new WorkOrderExecutionStatusResponse(workOrder.getId(), workOrder.getWorkOrderNo(),
                workOrder.getExecutionStatus(), workOrder.getSourceVersion());
    }
}
