package com.factoryflow.erp.workorder.controller;

import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderResponse;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import com.factoryflow.erp.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import com.factoryflow.erp.workorder.dto.WorkOrderUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(
            @Valid @RequestBody WorkOrderCreateRequest request
    ) {
        WorkOrder workOrder = workOrderService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(WorkOrderResponse.from(workOrder));
    }
    @PutMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderUpdateRequest request
    ) {
        return ResponseEntity.ok(WorkOrderResponse.from(workOrderService.update(id, request)));
    }
    @PostMapping("/{id}/resend")
    public ResponseEntity<WorkOrderResponse> resend(@PathVariable Long id) {
        return ResponseEntity.ok(WorkOrderResponse.from(workOrderService.resend(id)));
    }
}
