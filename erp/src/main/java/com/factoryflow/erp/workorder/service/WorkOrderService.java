package com.factoryflow.erp.workorder.service;

import com.factoryflow.erp.workorder.client.MesWorkOrderClient;
import com.factoryflow.erp.workorder.client.MesSyncRejectedResponseException;
import com.factoryflow.erp.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderSyncResponse;
import com.factoryflow.erp.workorder.entity.MesSyncAttempt;
import com.factoryflow.erp.workorder.entity.MesSyncResult;
import com.factoryflow.erp.workorder.repository.MesSyncAttemptRepository;
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
    private final MesSyncAttemptRepository mesSyncAttemptRepository;

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

    public WorkOrder resend(Long id) {
        WorkOrder workOrder = findWorkOrder(id);
        if (workOrder.getMesSyncStatus() != MesSyncStatus.FAILED) {
            throw new IllegalStateException("FAILED 작업지시만 재전송할 수 있습니다. 현재 상태: "
                    + workOrder.getMesSyncStatus());
        }
        synchronize(workOrder);
        return workOrder;
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("작업지시를 찾을 수 없습니다: " + id));
    }

    private void synchronize(WorkOrder workOrder) {
        workOrder.markSyncPending();
        WorkOrderSyncRequest request = WorkOrderSyncRequest.from(workOrder);
        MesSyncAttempt attempt = mesSyncAttemptRepository.save(MesSyncAttempt.pending(request));
        WorkOrderSyncResponse response;
        try {
            response = mesWorkOrderClient.sync(request);
        } catch (Exception exception) {
            String error = getErrorMessage(exception);
            MesSyncResult result = exception instanceof MesSyncRejectedResponseException rejected
                    ? MesSyncResult.valueOf(rejected.getResponse().result().name()) : null;
            attempt.fail(error, result);
            workOrder.markSyncFailed(error);
            return;
        }
        attempt.succeed(MesSyncResult.valueOf(response.result().name()));
        workOrder.markSyncSuccess();
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
