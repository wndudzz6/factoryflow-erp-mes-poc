package com.factoryflow.erp.workorder.service;

import com.factoryflow.erp.workorder.dto.MesSyncAttemptResponse;
import com.factoryflow.erp.workorder.dto.HistoryPageResponse;
import com.factoryflow.erp.workorder.repository.MesSyncAttemptRepository;
import com.factoryflow.erp.workorder.repository.WorkOrderRepository;
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
public class MesSyncAttemptQueryService {
    private final MesSyncAttemptRepository repository;
    private final WorkOrderRepository workOrderRepository;

    public HistoryPageResponse<MesSyncAttemptResponse> byWorkOrder(Long id, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        if (!workOrderRepository.existsById(id)) {
            throw new NoSuchElementException("작업지시를 찾을 수 없습니다: " + id);
        }
        return HistoryPageResponse.from(repository.findByWorkOrderId(id, pageable).map(MesSyncAttemptResponse::from));
    }

    public HistoryPageResponse<MesSyncAttemptResponse> byEvent(UUID eventId, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        if (!repository.existsByEventId(eventId)) {
            throw new NoSuchElementException("eventId 이력을 찾을 수 없습니다: " + eventId);
        }
        return HistoryPageResponse.from(repository.findByEventId(eventId, pageable).map(MesSyncAttemptResponse::from));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page는 0 이상, size는 1~100이어야 합니다.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt", "id"));
    }
}
