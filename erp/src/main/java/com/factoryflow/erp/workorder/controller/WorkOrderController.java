package com.factoryflow.erp.workorder.controller;

import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderCreateResponse;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import com.factoryflow.erp.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @PostMapping
    public ResponseEntity<WorkOrderCreateResponse> create(
            @RequestBody WorkOrderCreateRequest request
    ) {
        WorkOrder workOrder = workOrderService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(WorkOrderCreateResponse.from(workOrder));
    }
}