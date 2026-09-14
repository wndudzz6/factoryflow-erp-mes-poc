package com.factoryflow.erp.workorder.client;

import com.factoryflow.erp.workorder.dto.WorkOrderSyncResponse;

/** 유효한 과거 버전 응답도 ERP 동기화 성공으로 간주하지 않는다. */
public class MesSyncRejectedResponseException extends IllegalStateException {
    private final WorkOrderSyncResponse response;

    public MesSyncRejectedResponseException(WorkOrderSyncResponse response) {
        super("MES 동기화 결과 불일치: " + response);
        this.response = response;
    }

    public WorkOrderSyncResponse getResponse() { return response; }
}
