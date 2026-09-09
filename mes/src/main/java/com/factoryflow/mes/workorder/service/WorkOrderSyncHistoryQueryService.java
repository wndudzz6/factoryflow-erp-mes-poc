package com.factoryflow.mes.workorder.service;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncHistoryResponse;
import com.factoryflow.mes.workorder.dto.HistoryPageResponse;
import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.repository.WorkOrderSyncHistoryRepository;
import com.factoryflow.mes.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderSyncHistoryQueryService {
    private final WorkOrderSyncHistoryRepository repository;
    private final WorkOrderRepository workOrderRepository;

    public HistoryPageResponse<WorkOrderSyncHistoryResponse> byWorkOrder(Long id, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("작업지시를 찾을 수 없습니다: " + id));
        // 원천 식별자로 조회하여 생성 전 거부 이력도 같은 작업지시에 연결한다.
        return HistoryPageResponse.from(repository.findBySourceSystemAndExternalId(
                workOrder.getSourceSystem(), workOrder.getExternalId(), pageable).map(WorkOrderSyncHistoryResponse::from));
    }

    public HistoryPageResponse<WorkOrderSyncHistoryResponse> byEvent(UUID eventId, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        if (!repository.existsByEventId(eventId)) {
            throw new NoSuchElementException("eventId 이력을 찾을 수 없습니다: " + eventId);
        }
        return HistoryPageResponse.from(repository.findByEventId(eventId, pageable).map(WorkOrderSyncHistoryResponse::from));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page는 0 이상, size는 1~100이어야 합니다.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt", "id"));
    }
}
