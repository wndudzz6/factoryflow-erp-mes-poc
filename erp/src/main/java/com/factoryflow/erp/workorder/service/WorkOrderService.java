package com.factoryflow.erp.workorder.service;

import com.factoryflow.erp.workorder.client.MesWorkOrderClient;
import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderUpdateRequest;
import com.factoryflow.erp.workorder.entity.MesSyncStatus;
import java.util.NoSuchElementException;
import org.springframework.web.client.RestClientResponseException;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import com.factoryflow.erp.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final MesWorkOrderClient mesWorkOrderClient;

    public WorkOrder create(WorkOrderCreateRequest request) {
        validateDuplicateWorkOrderNo(request.workOrderNo());

        WorkOrder workOrder = WorkOrder.create(request);
        WorkOrder savedWorkOrder =
                workOrderRepository.save(workOrder);

        synchronize(savedWorkOrder);
        return savedWorkOrder;
    }

    public WorkOrder update(Long id, WorkOrderUpdateRequest request) {
        WorkOrder workOrder = findWorkOrder(id);
        if (workOrder.changePlan(request)) {
            synchronize(workOrder);
        }
        return workOrder;
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("작업지시를 찾을 수 없습니다: " + id));
    }

    private void synchronize(WorkOrder workOrder) {
        workOrder.markSyncPending();
        try {
            mesWorkOrderClient.sync(workOrder);
            workOrder.markSyncSuccess();
        } catch (Exception exception) {
            workOrder.markSyncFailed(getErrorMessage(exception));
        }
    }

    private void validateDuplicateWorkOrderNo(String workOrderNo) {
        if (workOrderRepository.existsByWorkOrderNo(workOrderNo)) {
            throw new IllegalArgumentException(
                    "이미 존재하는 작업지시 번호입니다: " + workOrderNo
            );
        }
    }

    private String getErrorMessage(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (!body.isBlank()) {
                return "MES HTTP " + responseException.getStatusCode().value() + ": " + body;
            }
        }
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}
