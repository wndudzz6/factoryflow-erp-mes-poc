package com.factoryflow.mes.workorder.exception;

import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import lombok.Getter;
import com.factoryflow.mes.workorder.entity.WorkOrderSyncResult;

@Getter
public class WorkOrderConflictException extends IllegalStateException {
    private final WorkOrderSyncResult historyResult;
    private final String workOrderNo;
    private final WorkOrderExecutionStatus executionStatus;

    public WorkOrderConflictException(WorkOrder workOrder, String reason) {
        this(workOrder, reason, WorkOrderSyncResult.REJECTED_PRODUCTION_STARTED);
    }

    public WorkOrderConflictException(WorkOrder workOrder, String reason, WorkOrderSyncResult historyResult) {
        super(reason);
        this.historyResult = historyResult;
        workOrderNo = workOrder.getWorkOrderNo();
        executionStatus = workOrder.getExecutionStatus();
    }
}
