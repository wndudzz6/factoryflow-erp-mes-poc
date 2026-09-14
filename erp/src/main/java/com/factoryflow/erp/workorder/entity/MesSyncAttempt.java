package com.factoryflow.erp.workorder.entity;

import com.factoryflow.erp.workorder.dto.WorkOrderSyncRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mes_sync_attempts", indexes = {
        @Index(name = "ix_attempt_order_time", columnList = "work_order_id,requested_at,id"),
        @Index(name = "ix_attempt_event", columnList = "event_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MesSyncAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;
    @Column(nullable = false)
    private String workOrderNo;
    // eventId는 시도 추적용이다. 반영 여부는 원천 식별자와 version으로 판단한다.
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(nullable = false)
    private String eventType;
    @Column(nullable = false)
    private int version;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private MesSyncStatus status;
    @Enumerated(EnumType.STRING)
    private MesSyncResult mesResult;
    @Column(length = 1000)
    private String errorReason;
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    public static MesSyncAttempt pending(WorkOrderSyncRequest request) {
        MesSyncAttempt attempt = new MesSyncAttempt();
        attempt.workOrderId = request.externalId();
        attempt.workOrderNo = request.workOrderNo();
        attempt.eventId = request.eventId();
        attempt.eventType = request.eventType();
        attempt.version = request.version();
        attempt.status = MesSyncStatus.PENDING;
        attempt.requestedAt = LocalDateTime.now();
        return attempt;
    }

    public void succeed(MesSyncResult result) {
        status = MesSyncStatus.SUCCESS;
        mesResult = result;
        completedAt = LocalDateTime.now();
    }

    public void fail(String reason, MesSyncResult result) {
        status = MesSyncStatus.FAILED;
        mesResult = result;
        errorReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000));
        completedAt = LocalDateTime.now();
    }
}
