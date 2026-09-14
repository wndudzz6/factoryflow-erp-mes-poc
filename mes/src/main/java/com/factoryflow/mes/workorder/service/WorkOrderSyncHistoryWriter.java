package com.factoryflow.mes.workorder.service;

import com.factoryflow.mes.workorder.entity.WorkOrderSyncHistory;
import com.factoryflow.mes.workorder.repository.WorkOrderSyncHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkOrderSyncHistoryWriter {
    private final WorkOrderSyncHistoryRepository repository;

    /** 별도 Bean 프록시를 통해 실행되어 원래 동기화의 409 롤백 이후에도 이력이 남는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(WorkOrderSyncHistory history) {
        repository.save(history);
    }
}
