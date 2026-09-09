package com.factoryflow.mes.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.factoryflow.mes.workorder.entity.WorkOrder;

@Schema(description = "receivedVersion은 수신 버전, appliedVersion은 현재 MES 적용 버전. id는 MES 내부 식별자.")
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
