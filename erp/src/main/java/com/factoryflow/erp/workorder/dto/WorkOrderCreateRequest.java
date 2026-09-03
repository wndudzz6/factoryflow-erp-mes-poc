package com.factoryflow.erp.workorder.dto;

import java.time.LocalDate;

public record WorkOrderCreateRequest(
        String workOrderNo,
        String productCode,
        int plannedQuantity,
        LocalDate dueDate,
        int priority,
        String routingCode,
        int routingRevision
) {
}