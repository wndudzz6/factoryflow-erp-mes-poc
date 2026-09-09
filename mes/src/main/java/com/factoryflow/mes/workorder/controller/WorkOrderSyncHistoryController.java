package com.factoryflow.mes.workorder.controller;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncHistoryResponse;
import com.factoryflow.mes.workorder.dto.HistoryPageResponse;
import com.factoryflow.mes.workorder.service.WorkOrderSyncHistoryQueryService;
import com.factoryflow.mes.global.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@ApiResponse(responseCode = "200", description = "최신순 이력 페이지", content = @Content(
        mediaType = "application/json", examples = @ExampleObject(name = "history", value = """
        {"content":[{"id":1,"mesWorkOrderId":11,"eventId":"11111111-1111-1111-1111-111111111111","sourceSystem":"ERP","externalId":7,"workOrderNo":"WO-001","receivedVersion":1,"previousVersion":null,"appliedVersion":1,"result":"CREATED","message":"신규 생성","receivedAt":"2026-09-09T12:00:00","completedAt":"2026-09-09T12:00:01"}],"page":0,"size":20,"totalElements":1,"totalPages":1}
        """)))
@ApiResponse(responseCode = "404", description = "작업지시 또는 eventId가 없음", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
        examples = @ExampleObject(value = """
        {"status":404,"message":"이력을 찾을 수 없습니다.","workOrderNo":null,"executionStatus":null}
        """)))
@ApiResponse(responseCode = "400", description = "식별자 형식 또는 페이징 값 오류", content = @Content(
        mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
        examples = @ExampleObject(value = """
        {"status":400,"message":"page는 0 이상, size는 1~100이어야 합니다.","workOrderNo":null,"executionStatus":null}
        """)))
public class WorkOrderSyncHistoryController {
    private final WorkOrderSyncHistoryQueryService service;

    @GetMapping("/work-orders/{id}/sync-history")
    @Operation(summary = "MES 작업지시별 동기화 이력 조회", description = "MES 내부 PK로 조회. 최신 시각 및 이력 ID 내림차순. 기존 작업지시의 이력이 없으면 빈 페이지.")
    public HistoryPageResponse<WorkOrderSyncHistoryResponse> byWorkOrder(
            @Parameter(description = "MES 작업지시 내부 PK") @PathVariable Long id,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 1~100", example = "20") @RequestParam(defaultValue = "20") int size) {
        return service.byWorkOrder(id, page, size);
    }

    @GetMapping("/sync-history/events/{eventId}")
    @Operation(summary = "eventId로 MES 동기화 이력 추적", description = "eventId는 추적용, version은 반영 여부 판정용. 같은 eventId의 모든 기록을 최신순 페이징 조회. 작업지시 참조 없이도 조회 가능.")
    public HistoryPageResponse<WorkOrderSyncHistoryResponse> byEvent(
            @Parameter(description = "ERP와 MES에 전달된 동일 추적 UUID") @PathVariable UUID eventId,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 1~100", example = "20") @RequestParam(defaultValue = "20") int size) {
        return service.byEvent(eventId, page, size);
    }
}
