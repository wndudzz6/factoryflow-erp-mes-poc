package com.factoryflow.mes.workorder.controller;

import com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest;
import com.factoryflow.mes.workorder.dto.WorkOrderSyncResponse;
import com.factoryflow.mes.workorder.dto.WorkOrderExecutionStatusRequest;
import com.factoryflow.mes.workorder.dto.WorkOrderExecutionStatusResponse;
import jakarta.validation.Valid;
import com.factoryflow.mes.workorder.service.WorkOrderSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.factoryflow.mes.global.exception.ApiError;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "MES 작업지시", description = "원천 계획 수신 및 생산 실행 상태 관리")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderSyncController {

    private final WorkOrderSyncService workOrderSyncService;

    @Operation(summary = "작업지시 동기화",
            description = "ERP만 허용하며 sourceSystem + externalId로 식별합니다. 동일 버전은 IGNORED_SAME_VERSION, 과거 버전은 IGNORED_OLD_VERSION으로 HTTP 200을 반환하고 데이터를 변경하지 않습니다. 높은 버전만 검증 후 반영합니다. PLANNED·READY에서는 주요 변경 허용, IN_PROGRESS·COMPLETED·CANCELLED에서는 수량·라우팅 코드·리비전 변경을 409로 거부합니다. 납기·우선순위 변경은 허용합니다. 작업지시 번호·품목 코드는 변경할 수 없습니다. eventId는 추적용이며 중복 판정은 원천 식별자와 버전을 사용합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderSyncRequest.class),
                            examples = @ExampleObject(name = "request", value = "{\"sourceSystem\":\"ERP\",\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"eventType\":\"WORK_ORDER_CREATED\",\"externalId\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":1,\"productCode\":\"PRODUCT-A001\",\"plannedQuantity\":100,\"dueDate\":\"2026-09-10\",\"priority\":1,\"routingCode\":\"ROUTING-A\",\"routingRevision\":1}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 결과에 따라 생성·갱신·미반영을 구분합니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderSyncResponse.class),
                            examples = {@ExampleObject(name = "CREATED", value = "{\"id\":11,\"workOrderNo\":\"WO-20260903-001\",\"receivedVersion\":1,\"appliedVersion\":1,\"result\":\"CREATED\",\"message\":\"신규 작업지시를 생성했습니다.\"}"),
                                    @ExampleObject(name = "UPDATED", value = "{\"id\":11,\"workOrderNo\":\"WO-20260903-001\",\"receivedVersion\":2,\"appliedVersion\":2,\"result\":\"UPDATED\",\"message\":\"최신 계획을 반영했습니다.\"}"),
                                    @ExampleObject(name = "IGNORED_SAME_VERSION", value = "{\"id\":11,\"workOrderNo\":\"WO-20260903-001\",\"receivedVersion\":2,\"appliedVersion\":2,\"result\":\"IGNORED_SAME_VERSION\",\"message\":\"동일 버전이 이미 적용되어 반영하지 않았습니다.\"}"),
                                    @ExampleObject(name = "IGNORED_OLD_VERSION", value = "{\"id\":11,\"workOrderNo\":\"WO-20260903-001\",\"receivedVersion\":1,\"appliedVersion\":2,\"result\":\"IGNORED_OLD_VERSION\",\"message\":\"현재 적용 버전보다 낮아 반영하지 않았습니다.\"}")})),
            @ApiResponse(responseCode = "400", description = "현재는 ERP 작업지시만 지원합니다. 또는 요청 값이 잘못되었습니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "400", value = "{\"status\":400,\"message\":\"현재는 ERP 작업지시만 지원합니다. 또는 요청 값이 잘못되었습니다.\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "409", description = "생산 시작 후 주요 변경, 식별 정보 변경 또는 중복 작업지시",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "productionStarted", value = "{\"status\":409,\"workOrderNo\":\"WO-20260903-001\",\"executionStatus\":\"IN_PROGRESS\",\"message\":\"생산 시작 후 또는 취소된 작업지시는 계획수량, 라우팅 코드, 라우팅 리비전을 변경할 수 없습니다.\"}")}))
    })
    @PostMapping("/sync")
    public ResponseEntity<WorkOrderSyncResponse> sync(
            @Valid @RequestBody WorkOrderSyncRequest request
    ) {
        return ResponseEntity.ok(workOrderSyncService.sync(request));
    }

    @Operation(summary = "생산 실행 상태 변경",
            description = "MES 내부 id를 사용합니다. PLANNED → READY 또는 IN_PROGRESS 또는 CANCELLED, READY → IN_PROGRESS 또는 CANCELLED, IN_PROGRESS → COMPLETED 또는 CANCELLED만 허용합니다. 같은 상태 요청은 무변경 성공입니다. COMPLETED·CANCELLED는 종료 상태이고 이전 상태로 돌아갈 수 없습니다. sourceVersion은 변경하지 않습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderExecutionStatusRequest.class),
                            examples = @ExampleObject(name = "request", value = "{\"executionStatus\":\"IN_PROGRESS\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경 완료",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderExecutionStatusResponse.class),
                            examples = {@ExampleObject(name = "started", value = "{\"id\":11,\"workOrderNo\":\"WO-20260903-001\",\"executionStatus\":\"IN_PROGRESS\",\"sourceVersion\":2}")})),
            @ApiResponse(responseCode = "400", description = "올바른 executionStatus를 지정해 주세요.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "400", value = "{\"status\":400,\"message\":\"올바른 executionStatus를 지정해 주세요.\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "404", description = "작업지시를 찾을 수 없습니다: 999",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "404", value = "{\"status\":404,\"message\":\"작업지시를 찾을 수 없습니다: 999\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "409", description = "허용하지 않는 상태 전이",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "invalidTransition", value = "{\"status\":409,\"message\":\"허용하지 않는 생산 상태 전이: IN_PROGRESS -> PLANNED\",\"workOrderNo\":\"WO-20260903-001\",\"executionStatus\":\"IN_PROGRESS\"}")}))
    })
    @PatchMapping("/{id}/execution-status")
    public ResponseEntity<WorkOrderExecutionStatusResponse> changeExecutionStatus(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderExecutionStatusRequest request
    ) {
        return ResponseEntity.ok(workOrderSyncService.changeExecutionStatus(id, request.executionStatus()));
    }
}
