package com.factoryflow.mes.workorder.controller;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.mes.workorder.dto.WorkOrderSyncResponse;
import com.factoryflow.mes.workorder.dto.WorkOrderExecutionStatusRequest;
import com.factoryflow.mes.workorder.dto.WorkOrderExecutionStatusResponse;
import jakarta.validation.Valid;
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
    public ResponseEntity<WorkOrderSyncResponse> sync(
            @Valid @RequestBody WorkOrderSyncRequest request
    ) {
        return ResponseEntity.ok(workOrderSyncService.sync(request));
    }
    @PatchMapping("/{id}/execution-status")
    public ResponseEntity<WorkOrderExecutionStatusResponse> changeExecutionStatus(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderExecutionStatusRequest request
    ) {
        return ResponseEntity.ok(workOrderSyncService.changeExecutionStatus(id, request.executionStatus()));
    }
}
