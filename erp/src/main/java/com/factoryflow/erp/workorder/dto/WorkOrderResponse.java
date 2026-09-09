package com.factoryflow.erp.workorder.dto;

import com.factoryflow.erp.workorder.entity.MesSyncStatus;
import com.factoryflow.erp.workorder.entity.WorkOrder;

public record WorkOrderResponse(
        Long id,
        String workOrderNo,
        int version,
        MesSyncStatus mesSyncStatus,
        String mesSyncError
) {

    public static WorkOrderResponse from(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getWorkOrderNo(),
                workOrder.getVersion(),
                workOrder.getMesSyncStatus(),
                workOrder.getMesSyncError()
        );
    }
}
