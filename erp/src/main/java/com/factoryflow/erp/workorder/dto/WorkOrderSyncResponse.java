package com.factoryflow.erp.workorder.dto;

public record WorkOrderSyncResponse(
        Long id,
        String workOrderNo,
        int receivedVersion,
        int appliedVersion,
        Result result,
        String message
) {
    public enum Result {
        CREATED, UPDATED, IGNORED_SAME_VERSION, IGNORED_OLD_VERSION
    }
}
