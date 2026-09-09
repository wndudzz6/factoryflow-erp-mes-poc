package com.factoryflow.erp.workorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.factoryflow.erp.workorder.entity.MesSyncStatus;
import com.factoryflow.erp.workorder.entity.WorkOrder;

@Schema(description = "ERP 처리 결과. mesSyncStatus: PENDING(전송 대기), SUCCESS(현재 버전 동기화), FAILED(전송 또는 결과 확인 실패).")
public record WorkOrderResponse(
        Long id,
        String workOrderNo,
        @Schema(description = "ERP 계획 버전. 계획 변경 시 증가하고 통신 재전송 시 유지") int version,
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
