package com.factoryflow.erp.workorder.entity;

import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderUpdateRequest;
import java.util.Objects;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_no", nullable = false, unique = true)
    private String workOrderNo;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "planned_quantity", nullable = false)
    private int plannedQuantity;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private int version;

    @Column(name = "routing_code")
    private String routingCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "routing_revision", nullable = false)
    private int routingRevision;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "mes_sync_status", nullable = false)
    private MesSyncStatus mesSyncStatus;

    @Column(name = "mes_sync_error", length = 1000)
    private String mesSyncError;

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

    public static WorkOrder create(WorkOrderCreateRequest request) {
        WorkOrder workOrder = new WorkOrder();

        workOrder.version = 1;
        workOrder.mesSyncStatus = MesSyncStatus.PENDING;

        workOrder.workOrderNo = request.workOrderNo();
        workOrder.productCode = request.productCode();
        workOrder.plannedQuantity = request.plannedQuantity();
        workOrder.dueDate = request.dueDate();
        workOrder.priority = request.priority();
        workOrder.version = 1;
        workOrder.routingCode = request.routingCode();
        workOrder.routingRevision = request.routingRevision();

        return workOrder;
    }

    public boolean changePlan(WorkOrderUpdateRequest request) {
        if (plannedQuantity == request.plannedQuantity()
                && Objects.equals(dueDate, request.dueDate())
                && priority == request.priority()
                && Objects.equals(routingCode, request.routingCode())
                && routingRevision == request.routingRevision()) {
            return false;
        }
        plannedQuantity = request.plannedQuantity();
        dueDate = request.dueDate();
        priority = request.priority();
        routingCode = request.routingCode();
        routingRevision = request.routingRevision();
        version++;
        markSyncPending();
        return true;
    }

    public void markSyncPending() {
        mesSyncStatus = MesSyncStatus.PENDING;
        mesSyncError = null;
    }

    public void markSyncSuccess() {
        this.mesSyncStatus = MesSyncStatus.SUCCESS;
        this.mesSyncError = null;
    }

    public void markSyncFailed(String message) {
        this.mesSyncStatus = MesSyncStatus.FAILED;
        this.mesSyncError = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
    }
}
