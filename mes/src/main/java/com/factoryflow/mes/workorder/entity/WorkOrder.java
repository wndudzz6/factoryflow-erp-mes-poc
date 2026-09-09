package com.factoryflow.mes.workorder.entity;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.mes.workorder.exception.WorkOrderConflictException;
import java.util.Objects;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_work_order_source_external",
                        columnNames = {"source_system", "external_id"}
                ),
                @UniqueConstraint(
                        name = "uk_work_order_no",
                        columnNames = "work_order_no"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_no", nullable = false)
    private String workOrderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "planned_quantity", nullable = false)
    private int plannedQuantity;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private int priority;

    @Column(name = "source_version", nullable = false)
    private int sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    private WorkOrderExecutionStatus executionStatus;

    @Column(name = "routing_code")
    private String routingCode;

    @Column(name = "routing_revision", nullable = false)
    private int routingRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static WorkOrder createFrom(WorkOrderSyncRequest request) {
        WorkOrder workOrder = new WorkOrder();

        workOrder.sourceSystem = request.sourceSystem();
        workOrder.externalId = request.externalId();
        workOrder.workOrderNo = request.workOrderNo();

        workOrder.productCode = request.productCode();
        workOrder.plannedQuantity = request.plannedQuantity();
        workOrder.dueDate = request.dueDate();
        workOrder.priority = request.priority();

        workOrder.sourceVersion = request.version();
        workOrder.executionStatus = WorkOrderExecutionStatus.PLANNED;

        workOrder.routingCode = request.routingCode();
        workOrder.routingRevision = request.routingRevision();

        return workOrder;
    }


    //서비스에서 검증한 작업계획(ERP) 변경사항을 현재 작업지시에 반영
    public void applyPlanChange(WorkOrderSyncRequest request) {
        this.productCode = request.productCode();
        this.plannedQuantity = request.plannedQuantity();
        this.dueDate = request.dueDate();
        this.priority = request.priority();

        this.routingCode = request.routingCode();
        this.routingRevision = request.routingRevision();

        this.sourceVersion = request.version();
    }

    public void validatePlanChange(WorkOrderSyncRequest request) {
        if (!Objects.equals(workOrderNo, request.workOrderNo())
                || !Objects.equals(productCode, request.productCode())) {
            throw new WorkOrderConflictException(this, "작업지시 번호와 품목 코드는 변경할 수 없습니다.",
                    WorkOrderSyncResult.REJECTED_IDENTITY_CONFLICT);
        }
        boolean majorChange = plannedQuantity != request.plannedQuantity()
                || !Objects.equals(routingCode, request.routingCode())
                || routingRevision != request.routingRevision();
        if (majorChange && executionStatus != WorkOrderExecutionStatus.PLANNED
                && executionStatus != WorkOrderExecutionStatus.READY) {
            throw new WorkOrderConflictException(this,
                    "생산 시작 후 또는 취소된 작업지시는 계획수량, 라우팅 코드, 라우팅 리비전을 변경할 수 없습니다.");
        }
    }

    public void changeExecutionStatus(WorkOrderExecutionStatus next) {
        if (next == executionStatus) {
            return;
        }
        boolean allowed = switch (executionStatus) {
            case PLANNED -> next == WorkOrderExecutionStatus.READY
                    || next == WorkOrderExecutionStatus.IN_PROGRESS
                    || next == WorkOrderExecutionStatus.CANCELLED;
            case READY -> next == WorkOrderExecutionStatus.IN_PROGRESS
                    || next == WorkOrderExecutionStatus.CANCELLED;
            case IN_PROGRESS -> next == WorkOrderExecutionStatus.COMPLETED
                    || next == WorkOrderExecutionStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new WorkOrderConflictException(this, "허용하지 않는 생산 상태 전이: "
                    + executionStatus + " -> " + next);
        }
        executionStatus = next;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
