package com.factoryflow.erp.workorder.dto;

import com.factoryflow.erp.workorder.entity.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record MesSyncAttemptResponse(
        Long id,
        @Schema(description = "ERP 작업지시 내부 PK") Long erpWorkOrderId,
        String workOrderNo,
        @Schema(description = "ERP 전송과 MES 수신을 연결하는 추적 ID. 멱등성 키가 아님") UUID eventId,
        String eventType,
        @Schema(description = "전송한 계획 버전. 변경 시 증가, 재전송 시 유지") int version,
        @Schema(description = "PENDING: 전송 중, SUCCESS: 응답 검증 성공, FAILED: 통신/업무 거부/계약 검증 실패") MesSyncStatus status,
        @Schema(description = "MES 정상 응답의 처리 결과. 정상 결과를 받지 못하면 null. OLD_VERSION은 FAILED") MesSyncResult mesResult,
        @Schema(description = "최대 1,000자의 오류 사유", maxLength = 1000) String errorReason,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    public static MesSyncAttemptResponse from(MesSyncAttempt value) {
        return new MesSyncAttemptResponse(value.getId(), value.getWorkOrderId(), value.getWorkOrderNo(),
                value.getEventId(), value.getEventType(), value.getVersion(), value.getStatus(),
                value.getMesResult(), value.getErrorReason(), value.getRequestedAt(), value.getCompletedAt());
    }
}
