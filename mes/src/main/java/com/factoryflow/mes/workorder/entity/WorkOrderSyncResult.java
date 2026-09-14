package com.factoryflow.mes.workorder.entity;

/** 영속 수신 이력의 결과. 성공 응답 계약에 없는 업무 거부도 포함한다. */
public enum WorkOrderSyncResult {
    CREATED, UPDATED, IGNORED_SAME_VERSION, IGNORED_OLD_VERSION,
    REJECTED_PRODUCTION_STARTED, REJECTED_IDENTITY_CONFLICT, REJECTED_UNSUPPORTED_SOURCE
}
