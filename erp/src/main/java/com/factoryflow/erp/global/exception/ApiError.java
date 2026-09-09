package com.factoryflow.erp.global.exception;

public record ApiError(int status, String message, String workOrderNo, String executionStatus) {
}
