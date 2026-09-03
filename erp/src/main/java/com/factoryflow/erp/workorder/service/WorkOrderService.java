package com.factoryflow.erp.workorder.service;

import com.factoryflow.erp.workorder.client.MesWorkOrderClient;
import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
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

        try {
            mesWorkOrderClient.sync(savedWorkOrder);
            savedWorkOrder.markSyncSuccess();
        } catch (Exception exception) {
            savedWorkOrder.markSyncFailed(getErrorMessage(exception));
        }

        return savedWorkOrder;
    }

    private void validateDuplicateWorkOrderNo(String workOrderNo) {
        if (workOrderRepository.existsByWorkOrderNo(workOrderNo)) {
            throw new IllegalArgumentException(
                    "이미 존재하는 작업지시 번호입니다: " + workOrderNo
            );
        }
    }

    private String getErrorMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message;
    }
}