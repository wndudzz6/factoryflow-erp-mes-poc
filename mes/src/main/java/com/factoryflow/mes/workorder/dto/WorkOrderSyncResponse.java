package com.factoryflow.mes.workorder.dto;

import com.factoryflow.mes.workorder.entity.WorkOrder;

public record WorkOrderSyncResponse(
        Long id,
        String workOrderNo,
        int receivedVersion,
        int appliedVersion,
        Result result,
        String message
) {
    public enum Result {
        CREATED, UPDATED, IGNORED_SAME_VERSION, IGNORED_OLD_VERSION
    }

    public static WorkOrderSyncResponse from(WorkOrder workOrder, int receivedVersion,
                                             Result result, String message) {
        return new WorkOrderSyncResponse(workOrder.getId(), workOrder.getWorkOrderNo(),
                receivedVersion, workOrder.getSourceVersion(), result, message);
    }
}
