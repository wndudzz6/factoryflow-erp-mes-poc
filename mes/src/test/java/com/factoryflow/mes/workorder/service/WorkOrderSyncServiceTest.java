package com.factoryflow.mes.workorder.service;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.mes.workorder.entity.SourceSystem;
import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import com.factoryflow.mes.workorder.dto.WorkOrderSyncResponse;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import com.factoryflow.mes.workorder.exception.WorkOrderConflictException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import static com.factoryflow.mes.workorder.dto.WorkOrderSyncResponse.Result.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderSyncServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @InjectMocks
    private WorkOrderSyncService workOrderSyncService;

    private WorkOrderSyncRequest request;

    @BeforeEach
    void setUp() {
        request = createRequest(
                SourceSystem.ERP,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "WORK_ORDER_CREATED",
                1L,
                1,
                100
        );
    }

    private WorkOrderSyncRequest createRequest(
            SourceSystem sourceSystem,
            UUID eventId,
            String eventType,
            Long externalId,
            int version,
            int plannedQuantity
    ) {
        return new WorkOrderSyncRequest(
                sourceSystem,
                eventId,
                eventType,

                externalId,
                "WO-20260903-001",
                version,

                "PRODUCT-A001",
                plannedQuantity,
                LocalDate.of(2026, 9, 10),
                1,

                "ROUTING-A",
                1
        );
    }

    private WorkOrder createExistingWorkOrder(
            int version,
            int plannedQuantity
    ) {
        WorkOrderSyncRequest existingRequest = createRequest(
                SourceSystem.ERP,
                UUID.randomUUID(),
                "WORK_ORDER_CREATED",
                1L,
                version,
                plannedQuantity
        );

        return WorkOrder.createFrom(existingRequest);
    }

    @Test
    @DisplayName("없는 작업지시를 수신하면 MES에 신규 저장한다")
    void 신규_작업지시는_MES에_저장한다() {
        // given
        when(workOrderRepository.findBySourceSystemAndExternalId(
                SourceSystem.ERP,
                1L
        )).thenReturn(Optional.empty());

        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        assertThat(workOrderSyncService.sync(request).result()).isEqualTo(CREATED);

        // then
        ArgumentCaptor<WorkOrder> captor =
                ArgumentCaptor.forClass(WorkOrder.class);

        verify(workOrderRepository).save(captor.capture());

        WorkOrder savedWorkOrder = captor.getValue();

        assertThat(savedWorkOrder.getSourceSystem())
                .isEqualTo(SourceSystem.ERP);
        assertThat(savedWorkOrder.getExternalId()).isEqualTo(1L);
        assertThat(savedWorkOrder.getWorkOrderNo())
                .isEqualTo("WO-20260903-001");
        assertThat(savedWorkOrder.getProductCode())
                .isEqualTo("PRODUCT-A001");
        assertThat(savedWorkOrder.getPlannedQuantity()).isEqualTo(100);
        assertThat(savedWorkOrder.getSourceVersion()).isEqualTo(1);
        assertThat(savedWorkOrder.getRoutingCode())
                .isEqualTo("ROUTING-A");
        assertThat(savedWorkOrder.getRoutingRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 버전 요청은 기존 작업지시를 변경하지 않는다")
    void 동일_버전_요청은_변경하지_않는다() {
        // given
        WorkOrder existing = createExistingWorkOrder(2, 100);

        WorkOrderSyncRequest sameVersionRequest = createRequest(
                SourceSystem.ERP,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "WORK_ORDER_UPDATED",
                1L,
                2,
                999
        );

        when(workOrderRepository.findBySourceSystemAndExternalId(
                SourceSystem.ERP,
                1L
        )).thenReturn(Optional.of(existing));

        // when
        assertThat(workOrderSyncService.sync(sameVersionRequest).result()).isEqualTo(IGNORED_SAME_VERSION);

        // then
        assertThat(existing.getPlannedQuantity()).isEqualTo(100);
        assertThat(existing.getSourceVersion()).isEqualTo(2);

        verify(workOrderRepository, never())
                .save(any(WorkOrder.class));
    }

    @Test
    @DisplayName("과거 버전이 뒤늦게 도착하면 기존 작업지시를 변경하지 않는다")
    void 과거_버전은_변경하지_않는다() {
        // given
        WorkOrder existing = createExistingWorkOrder(3, 300);

        WorkOrderSyncRequest oldVersionRequest = createRequest(
                SourceSystem.ERP,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "WORK_ORDER_UPDATED",
                1L,
                2,
                200
        );

        when(workOrderRepository.findBySourceSystemAndExternalId(
                SourceSystem.ERP,
                1L
        )).thenReturn(Optional.of(existing));

        // when
        assertThat(workOrderSyncService.sync(oldVersionRequest).result()).isEqualTo(IGNORED_OLD_VERSION);

        // then
        assertThat(existing.getPlannedQuantity()).isEqualTo(300);
        assertThat(existing.getSourceVersion()).isEqualTo(3);

        verify(workOrderRepository, never())
                .save(any(WorkOrder.class));
    }

    @Test
    @DisplayName("더 높은 버전을 수신하면 작업지시를 갱신한다")
    void 최신_버전은_작업지시에_반영한다() {
        // given
        WorkOrder existing = createExistingWorkOrder(2, 100);

        WorkOrderSyncRequest latestRequest = createRequest(
                SourceSystem.ERP,
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "WORK_ORDER_UPDATED",
                1L,
                3,
                500
        );

        when(workOrderRepository.findBySourceSystemAndExternalId(
                SourceSystem.ERP,
                1L
        )).thenReturn(Optional.of(existing));

        // when
        WorkOrderSyncResponse result = workOrderSyncService.sync(latestRequest);

        // then
        assertThat(existing.getPlannedQuantity()).isEqualTo(500);
        assertThat(result.appliedVersion()).isEqualTo(3);
        assertThat(result.result()).isEqualTo(UPDATED);
    }

    @Test
    @DisplayName("APS에서 전달된 작업지시는 거부한다")
    void APS_요청은_거부한다() {
        // given
        WorkOrderSyncRequest apsRequest = createRequest(
                SourceSystem.APS,
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "WORK_ORDER_CREATED",
                1L,
                1,
                100
        );

        // when & then
        assertThatThrownBy(() -> workOrderSyncService.sync(apsRequest))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(workOrderRepository);
    }

    private WorkOrder prepared(WorkOrderExecutionStatus status) {
        WorkOrder workOrder = createExistingWorkOrder(1, 100);
        if (status == WorkOrderExecutionStatus.COMPLETED) {
            workOrder.changeExecutionStatus(WorkOrderExecutionStatus.IN_PROGRESS);
        }
        workOrder.changeExecutionStatus(status);
        when(workOrderRepository.findBySourceSystemAndExternalId(SourceSystem.ERP, 1L))
                .thenReturn(Optional.of(workOrder));
        return workOrder;
    }

    private WorkOrderSyncRequest changed(int quantity, String routing, int revision) {
        return new WorkOrderSyncRequest(SourceSystem.ERP, UUID.randomUUID(), "WORK_ORDER_UPDATED",
                1L, request.workOrderNo(), 2, request.productCode(), quantity,
                request.dueDate().plusDays(1), 2, routing, revision);
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderExecutionStatus.class, names = {"PLANNED", "READY"})
    void majorChangesAllowedBeforeProduction(WorkOrderExecutionStatus status) {
        WorkOrder existing = prepared(status);
        assertThat(workOrderSyncService.sync(changed(200, "ROUTING-B", 2)).result()).isEqualTo(UPDATED);
        assertThat(existing.getPlannedQuantity()).isEqualTo(200);
        assertThat(existing.getRoutingCode()).isEqualTo("ROUTING-B");
        assertThat(existing.getRoutingRevision()).isEqualTo(2);
        assertThat(existing.getSourceVersion()).isEqualTo(2);
        assertThat(existing.getExecutionStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @CsvSource({"200,ROUTING-A,1", "100,ROUTING-B,1", "100,ROUTING-A,2"})
    void eachMajorFieldIsBlockedAfterStart(int quantity, String routing, int revision) {
        WorkOrder existing = prepared(WorkOrderExecutionStatus.IN_PROGRESS);
        assertThatThrownBy(() -> workOrderSyncService.sync(changed(quantity, routing, revision)))
                .isInstanceOf(WorkOrderConflictException.class).hasMessageContaining("생산 시작 후");
        assertThat(existing.getPlannedQuantity()).isEqualTo(100);
        assertThat(existing.getRoutingCode()).isEqualTo("ROUTING-A");
        assertThat(existing.getRoutingRevision()).isEqualTo(1);
        assertThat(existing.getDueDate()).isEqualTo(request.dueDate());
        assertThat(existing.getPriority()).isEqualTo(1);
        assertThat(existing.getSourceVersion()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = WorkOrderExecutionStatus.class, names = {"COMPLETED", "CANCELLED"})
    void terminalStatesBlockMajorChanges(WorkOrderExecutionStatus status) {
        WorkOrder existing = prepared(status);
        assertThatThrownBy(() -> workOrderSyncService.sync(changed(200, "ROUTING-A", 1)))
                .isInstanceOf(WorkOrderConflictException.class);
        assertThat(existing.getSourceVersion()).isEqualTo(1);
    }

    @Test
    void dueDateAndPriorityCanChangeAfterStart() {
        WorkOrder existing = prepared(WorkOrderExecutionStatus.IN_PROGRESS);
        assertThat(workOrderSyncService.sync(changed(100, "ROUTING-A", 1)).result()).isEqualTo(UPDATED);
        assertThat(existing.getDueDate()).isEqualTo(request.dueDate().plusDays(1));
        assertThat(existing.getPriority()).isEqualTo(2);
        assertThat(existing.getExecutionStatus()).isEqualTo(WorkOrderExecutionStatus.IN_PROGRESS);
    }

    @Test
    void repeatedVersionIsIgnoredEvenAfterProductionStarts() {
        WorkOrder existing = prepared(WorkOrderExecutionStatus.IN_PROGRESS);
        assertThat(workOrderSyncService.sync(request).result()).isEqualTo(IGNORED_SAME_VERSION);
        assertThat(existing.getSourceVersion()).isEqualTo(1);
    }

    @Test
    void productionStateCannotGoBackToPlanned() {
        WorkOrder existing = createExistingWorkOrder(1, 100);
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(existing));
        assertThat(workOrderSyncService.changeExecutionStatus(1L, WorkOrderExecutionStatus.IN_PROGRESS)
                .executionStatus()).isEqualTo(WorkOrderExecutionStatus.IN_PROGRESS);
        assertThatThrownBy(() -> workOrderSyncService.changeExecutionStatus(1L, WorkOrderExecutionStatus.PLANNED))
                .isInstanceOf(WorkOrderConflictException.class);
        workOrderSyncService.changeExecutionStatus(1L, WorkOrderExecutionStatus.COMPLETED);
        assertThatThrownBy(() -> workOrderSyncService.changeExecutionStatus(1L, WorkOrderExecutionStatus.IN_PROGRESS))
                .isInstanceOf(WorkOrderConflictException.class);
    }
}
