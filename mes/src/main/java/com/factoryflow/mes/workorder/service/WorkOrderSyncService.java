package com.factoryflow.mes.workorder.service;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
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

    @Transactional
    public WorkOrder sync(WorkOrderSyncRequest request) {

        if (request.sourceSystem() != SourceSystem.ERP) {
            throw new IllegalArgumentException(
                    "현재는 ERP 작업지시만 지원합니다."
            );
        }
        return workOrderRepository
                .findBySourceSystemAndExternalId(
                        request.sourceSystem(),
                        request.externalId()
                )
                .map(existing -> updateExisting(existing, request))
                .orElseGet(() -> createNew(request));
    }

    private WorkOrder createNew(WorkOrderSyncRequest request) {
        WorkOrder workOrder = WorkOrder.createFrom(request);
        return workOrderRepository.save(workOrder);
    }

    private WorkOrder updateExisting(
            WorkOrder existing,
            WorkOrderSyncRequest request
    ) {
        // 동일 요청 재전송
        if (request.version() == existing.getSourceVersion()) {
            return existing;
        }

        // 과거 버전이 뒤늦게 도착
        if (request.version() < existing.getSourceVersion()) {
            return existing;
        }

        // 더 최신인 계획(ERP) 반영
        existing.applyPlanChange(request);
        return existing;
    }
}