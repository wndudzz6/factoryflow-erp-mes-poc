package com.factoryflow.erp.workorder.service;

import com.factoryflow.erp.workorder.client.MesWorkOrderClient;
import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
import com.factoryflow.erp.workorder.entity.MesSyncStatus;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import com.factoryflow.erp.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private MesWorkOrderClient mesWorkOrderClient;

    @InjectMocks
    private WorkOrderService workOrderService;

    private WorkOrderCreateRequest request;

    @BeforeEach
    void setUp() {
        request = new WorkOrderCreateRequest(
                "WO-20260903-TEST-001",
                "PRODUCT-A001",
                100,
                LocalDate.of(2026, 9, 10),
                1,
                "ROUTING-A",
                1
        );
    }

    @Test
    @DisplayName("작업지시를 ERP에 저장하고 MES로 전송한다")
    void 작업지시를_저장하고_MES로_전송한다() {
        // given
        when(workOrderRepository.existsByWorkOrderNo(
                request.workOrderNo()
        )).thenReturn(false);

        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        WorkOrder result = workOrderService.create(request);

        // then
        assertThat(result.getWorkOrderNo())
                .isEqualTo("WO-20260903-TEST-001");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getMesSyncStatus())
                .isEqualTo(MesSyncStatus.SUCCESS);
        assertThat(result.getMesSyncError()).isNull();

        verify(workOrderRepository)
                .existsByWorkOrderNo("WO-20260903-TEST-001");
        verify(workOrderRepository)
                .save(any(WorkOrder.class));
        verify(mesWorkOrderClient)
                .sync(result);
    }

    @Test
    @DisplayName("MES 전송에 실패해도 ERP 작업지시는 유지하고 실패 상태를 기록한다")
    void MES_전송에_실패해도_ERP_작업지시는_유지한다() {
        // given
        when(workOrderRepository.existsByWorkOrderNo(
                request.workOrderNo()
        )).thenReturn(false);

        when(workOrderRepository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new RuntimeException("MES connection failed"))
                .when(mesWorkOrderClient)
                .sync(any(WorkOrder.class));

        // when
        WorkOrder result = workOrderService.create(request);

        // then
        assertThat(result.getWorkOrderNo())
                .isEqualTo("WO-20260903-TEST-001");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getMesSyncStatus())
                .isEqualTo(MesSyncStatus.FAILED);
        assertThat(result.getMesSyncError())
                .contains("MES connection failed");

        verify(workOrderRepository)
                .save(any(WorkOrder.class));
        verify(mesWorkOrderClient)
                .sync(result);
    }

    @Test
    @DisplayName("이미 존재하는 작업지시 번호로 생성할 수 없다")
    void 중복된_작업지시번호로_생성할_수_없다() {
        // given
        when(workOrderRepository.existsByWorkOrderNo(
                request.workOrderNo()
        )).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> workOrderService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 작업지시 번호");

        verify(workOrderRepository)
                .existsByWorkOrderNo(request.workOrderNo());
        verify(workOrderRepository, never())
                .save(any(WorkOrder.class));
        verifyNoInteractions(mesWorkOrderClient);
    }
}