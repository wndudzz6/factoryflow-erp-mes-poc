package com.factoryflow.mes.global.exception;

public record ApiError(int status, String message, String workOrderNo, String executionStatus) {
}
