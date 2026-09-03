package com.factoryflow.erp.workorder.dto;

import com.factoryflow.erp.workorder.entity.MesSyncStatus;
import com.factoryflow.erp.workorder.entity.WorkOrder;

public record WorkOrderCreateResponse(
        Long id,
        String workOrderNo,
        int version,
        MesSyncStatus mesSyncStatus,
        String mesSyncError
) {

    public static WorkOrderCreateResponse from(WorkOrder workOrder) {
        return new WorkOrderCreateResponse(
                workOrder.getId(),
                workOrder.getWorkOrderNo(),
                workOrder.getVersion(),
                workOrder.getMesSyncStatus(),
                workOrder.getMesSyncError()
        );
    }
}