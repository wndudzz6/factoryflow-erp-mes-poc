package com.factoryflow.mes.workorder.dto;

import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;

public record WorkOrderExecutionStatusResponse(Long id, String workOrderNo,
                                               WorkOrderExecutionStatus executionStatus,
                                               int sourceVersion) {
    public static WorkOrderExecutionStatusResponse from(WorkOrder workOrder) {
        return new WorkOrderExecutionStatusResponse(workOrder.getId(), workOrder.getWorkOrderNo(),
                workOrder.getExecutionStatus(), workOrder.getSourceVersion());
    }
}
