package com.factoryflow.mes.workorder.entity;


public enum WorkOrderExecutionStatus {
    PLANNED,       // 계획됨
    READY,         // 작업 대기
    IN_PROGRESS,   // 작업 중
    COMPLETED,     // 완료
    CANCELLED      // 취소
}