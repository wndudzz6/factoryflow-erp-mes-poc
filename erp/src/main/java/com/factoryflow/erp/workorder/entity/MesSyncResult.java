package com.factoryflow.erp.workorder.entity;

/** MES 응답의 처리 결과. 전송 시도 상태와 구분한다. */
public enum MesSyncResult {
    CREATED, UPDATED, IGNORED_SAME_VERSION, IGNORED_OLD_VERSION
}
