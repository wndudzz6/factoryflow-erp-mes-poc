package com.factoryflow.mes.workorder.dto;

import com.factoryflow.mes.workorder.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderSyncHistoryResponse(
        Long id,
        @Schema(description = "MES 내부 작업지시 PK. 신규 요청 거부 시 null일 수 있음") Long mesWorkOrderId,
        @Schema(description = "ERP 전송과 연결하는 추적 ID. 동일 ID로 여러 수신 기록 가능") UUID eventId,
        SourceSystem sourceSystem,
        @Schema(description = "원천 시스템의 작업지시 PK. 현재는 ERP ID이며 MES 내부 ID와 다름") Long externalId,
        String workOrderNo,
        @Schema(description = "수신한 ERP 계획 버전. 반영 여부 판정에 사용") int receivedVersion,
        @Schema(description = "처리 직전 MES 버전. 기존 작업지시가 없으면 null") Integer previousVersion,
        @Schema(description = "처리 후 MES 버전. 거부 시 기존 버전 유지, 신규 거부 시 null") Integer appliedVersion,
        @Schema(description = "CREATED/UPDATED: 반영, IGNORED_*: 미반영, REJECTED_*: 업무 거부") WorkOrderSyncResult result,
        @Schema(description = "처리 메시지 또는 거부 사유, 최대 1,000자", maxLength = 1000) String message,
        LocalDateTime receivedAt,
        LocalDateTime completedAt
) {
    public static WorkOrderSyncHistoryResponse from(WorkOrderSyncHistory value) {
        return new WorkOrderSyncHistoryResponse(value.getId(), value.getMesWorkOrderId(), value.getEventId(),
                value.getSourceSystem(), value.getExternalId(), value.getWorkOrderNo(), value.getReceivedVersion(),
                value.getPreviousVersion(), value.getAppliedVersion(), value.getResult(), value.getMessage(),
                value.getReceivedAt(), value.getCompletedAt());
    }
}
