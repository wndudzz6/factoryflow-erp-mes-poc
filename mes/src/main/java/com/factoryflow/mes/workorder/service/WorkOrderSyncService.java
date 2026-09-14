package com.factoryflow.mes.workorder.service;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.mes.workorder.dto.WorkOrderSyncResponse;
import com.factoryflow.mes.workorder.dto.WorkOrderExecutionStatusResponse;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import java.util.NoSuchElementException;
import java.time.LocalDateTime;
import com.factoryflow.mes.workorder.entity.WorkOrderSyncHistory;
import com.factoryflow.mes.workorder.entity.WorkOrderSyncResult;
import com.factoryflow.mes.workorder.exception.WorkOrderConflictException;
import com.factoryflow.mes.workorder.repository.WorkOrderSyncHistoryRepository;
import static com.factoryflow.mes.workorder.dto.WorkOrderSyncResponse.Result.*;
import com.factoryflow.mes.workorder.entity.SourceSystem;
import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkOrderSyncService {
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderSyncHistoryRepository historyRepository;
    private final WorkOrderSyncHistoryWriter historyWriter;

    @Transactional
    public WorkOrderSyncResponse sync(WorkOrderSyncRequest request) {

        LocalDateTime receivedAt = LocalDateTime.now();
        if (request.sourceSystem() != SourceSystem.ERP) {
            String reason = "현재는 ERP 작업지시만 지원합니다.";
            historyWriter.recordRejection(WorkOrderSyncHistory.completed(request, null, null, null,
                    WorkOrderSyncResult.REJECTED_UNSUPPORTED_SOURCE, reason, receivedAt));
            throw new IllegalArgumentException(reason);
        }
        WorkOrder existing = workOrderRepository
                .findBySourceSystemAndExternalId(request.sourceSystem(), request.externalId()).orElse(null);
        Integer previousVersion = existing == null ? null : existing.getSourceVersion();
        // DB 제약 위반 전에 업무 충돌로 판정하여 신규 작업지시가 없는 거부도 기록한다.
        if (existing == null && workOrderRepository.findByWorkOrderNo(request.workOrderNo()).isPresent()) {
            String reason = "작업지시 번호가 다른 원천 작업지시에 이미 사용되었습니다: " + request.workOrderNo();
            historyWriter.recordRejection(WorkOrderSyncHistory.completed(request, null, null, null,
                    WorkOrderSyncResult.REJECTED_IDENTITY_CONFLICT, reason, receivedAt));
            throw new IllegalStateException(reason);
        }
        WorkOrderSyncResponse response;
        try {
            response = existing == null ? createNew(request) : updateExisting(existing, request);
        } catch (WorkOrderConflictException exception) {
            // validatePlanChange는 어떤 계획값도 변경하기 전에 예외를 발생시킨다.
            historyWriter.recordRejection(WorkOrderSyncHistory.completed(request, existing.getId(),
                    previousVersion, previousVersion, exception.getHistoryResult(), exception.getMessage(), receivedAt));
            throw exception;
        }
        historyRepository.save(WorkOrderSyncHistory.completed(request, response.id(), previousVersion,
                response.appliedVersion(), WorkOrderSyncResult.valueOf(response.result().name()),
                response.message(), receivedAt));
        return response;
    }

    private WorkOrderSyncResponse createNew(WorkOrderSyncRequest request) {
        WorkOrder workOrder = WorkOrder.createFrom(request);
        WorkOrder saved = workOrderRepository.save(workOrder);
        return WorkOrderSyncResponse.from(saved, request.version(), CREATED, "신규 작업지시를 생성했습니다.");
    }

    private WorkOrderSyncResponse updateExisting(
            WorkOrder existing,
            WorkOrderSyncRequest request
    ) {
        // 동일 요청 재전송
        if (request.version() == existing.getSourceVersion()) {
            return WorkOrderSyncResponse.from(existing, request.version(),
                    IGNORED_SAME_VERSION, "동일 버전이 이미 적용되어 반영하지 않았습니다.");
        }

        // 과거 버전이 뒤늦게 도착
        if (request.version() < existing.getSourceVersion()) {
            return WorkOrderSyncResponse.from(existing, request.version(),
                    IGNORED_OLD_VERSION, "현재 적용 버전보다 낮아 반영하지 않았습니다.");
        }

        // 더 최신인 계획(ERP) 반영
        existing.validatePlanChange(request);
        existing.applyPlanChange(request);
        return WorkOrderSyncResponse.from(existing, request.version(), UPDATED, "최신 계획을 반영했습니다.");
    }
    @Transactional
    public WorkOrderExecutionStatusResponse changeExecutionStatus(Long id, WorkOrderExecutionStatus status) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("작업지시를 찾을 수 없습니다: " + id));
        workOrder.changeExecutionStatus(status);
        return WorkOrderExecutionStatusResponse.from(workOrder);
    }
}
