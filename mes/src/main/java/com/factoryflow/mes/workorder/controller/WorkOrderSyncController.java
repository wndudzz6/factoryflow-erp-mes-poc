package com.factoryflow.mes.workorder.controller;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.service.WorkOrderSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderSyncController {

    private final WorkOrderSyncService workOrderSyncService;

    @PostMapping("/sync")
    public ResponseEntity<WorkOrder> sync(
            @RequestBody WorkOrderSyncRequest request
    ) {
        WorkOrder workOrder = workOrderSyncService.sync(request);
        return ResponseEntity.ok(workOrder);
    }
}