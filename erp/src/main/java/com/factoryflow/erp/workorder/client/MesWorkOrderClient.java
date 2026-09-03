package com.factoryflow.erp.workorder.client;

import com.factoryflow.erp.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MesWorkOrderClient {

    private final RestClient mesRestClient;

    public void sync(WorkOrder workOrder) {
        WorkOrderSyncRequest request =
                WorkOrderSyncRequest.created(workOrder);

        mesRestClient.post()
                .uri("/api/work-orders/sync")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}