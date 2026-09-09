package com.factoryflow.mes.workorder.entity;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "work_order_sync_history", indexes = {
        @Index(name = "ix_history_source_time", columnList = "source_system,external_id,received_at,id"),
        @Index(name = "ix_history_event", columnList = "event_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrderSyncHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 스칼라 ID: 신규 요청 거부는 null이며, 별도 거부 이력 트랜잭션에 FK 잠금을 만들지 않는다.
    private Long mesWorkOrderId;
    // 반복된 동일 HTTP 요청도 별도 행으로 기록한다. eventId는 유일성/멱등성 키가 아니다.
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Enumerated(EnumType.STRING) @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;
    @Column(name = "external_id", nullable = false)
    private Long externalId;
    @Column(nullable = false)
    private String workOrderNo;
    @Column(nullable = false)
    private int receivedVersion;
    private Integer previousVersion;
    private Integer appliedVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private WorkOrderSyncResult result;
    @Column(length = 1000)
    private String message;
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
    @Column(nullable = false)
    private LocalDateTime completedAt;

    public static WorkOrderSyncHistory completed(WorkOrderSyncRequest request, Long mesWorkOrderId,
            Integer previousVersion, Integer appliedVersion, WorkOrderSyncResult result,
            String message, LocalDateTime receivedAt) {
        WorkOrderSyncHistory history = new WorkOrderSyncHistory();
        history.mesWorkOrderId = mesWorkOrderId;
        history.eventId = request.eventId();
        history.sourceSystem = request.sourceSystem();
        history.externalId = request.externalId();
        history.workOrderNo = request.workOrderNo();
        history.receivedVersion = request.version();
        history.previousVersion = previousVersion;
        history.appliedVersion = appliedVersion;
        history.result = result;
        history.message = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
        history.receivedAt = receivedAt;
        history.completedAt = LocalDateTime.now();
        return history;
    }
}
