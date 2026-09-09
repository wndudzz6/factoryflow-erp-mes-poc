package com.factoryflow.erp.workorder.client;

import com.factoryflow.erp.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderSyncResponse;
import java.util.Objects;
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
                WorkOrderSyncRequest.from(workOrder);

        WorkOrderSyncResponse response = mesRestClient.post()
                .uri("/api/work-orders/sync")
                .body(request)
                .retrieve()
                .body(WorkOrderSyncResponse.class);

        if (response == null || response.result() == null
                || !Objects.equals(response.workOrderNo(), workOrder.getWorkOrderNo())
                || response.receivedVersion() != workOrder.getVersion()
                || response.appliedVersion() != workOrder.getVersion()
                || response.result() == WorkOrderSyncResponse.Result.IGNORED_OLD_VERSION) {
            throw new IllegalStateException("MES 동기화 결과 불일치: " + response);
        }
    }
}
