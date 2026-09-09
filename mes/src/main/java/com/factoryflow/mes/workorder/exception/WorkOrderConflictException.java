package com.factoryflow.mes.workorder.exception;

import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import lombok.Getter;

@Getter
public class WorkOrderConflictException extends IllegalStateException {
    private final String workOrderNo;
    private final WorkOrderExecutionStatus executionStatus;

    public WorkOrderConflictException(WorkOrder workOrder, String reason) {
        super(reason);
        workOrderNo = workOrder.getWorkOrderNo();
        executionStatus = workOrder.getExecutionStatus();
    }
}
