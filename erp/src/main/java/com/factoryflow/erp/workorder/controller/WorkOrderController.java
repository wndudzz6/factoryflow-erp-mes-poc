package com.factoryflow.erp.workorder.controller;

import com.factoryflow.erp.workorder.dto.WorkOrderCreateRequest;
import com.factoryflow.erp.workorder.dto.WorkOrderResponse;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import com.factoryflow.erp.workorder.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.factoryflow.erp.global.exception.ApiError;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import com.factoryflow.erp.workorder.dto.WorkOrderUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "ERP 작업지시", description = "ERP 원본 계획 관리 및 MES 동기화")
@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "작업지시 생성",
            description = "버전 1, PENDING으로 생성한 원본 계획을 MES로 전송합니다. ERP 저장 성공은 HTTP 201이며, MES 전송 결과는 SUCCESS 또는 FAILED와 mesSyncError로 확인합니다. 계획수량·라우팅 리비전은 1 이상, 우선순위는 0 이상입니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderCreateRequest.class),
                            examples = @ExampleObject(name = "request", value = "{\"workOrderNo\":\"WO-20260903-001\",\"productCode\":\"PRODUCT-A001\",\"plannedQuantity\":100,\"dueDate\":\"2026-09-10\",\"priority\":1,\"routingCode\":\"ROUTING-A\",\"routingRevision\":1}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "ERP 처리 완료. MES 전송 결과는 mesSyncStatus 확인.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderResponse.class),
                            examples = {@ExampleObject(name = "success", value = "{\"id\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":1,\"mesSyncStatus\":\"SUCCESS\",\"mesSyncError\":null}"),
                                    @ExampleObject(name = "mesFailure", value = "{\"id\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":1,\"mesSyncStatus\":\"FAILED\",\"mesSyncError\":\"MES HTTP 409: 생산 시작 후 주요 계획은 변경할 수 없습니다.\"}")})),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 값 또는 중복 작업지시 번호",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "400", value = "{\"status\":400,\"message\":\"잘못된 요청 값 또는 중복 작업지시 번호\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "409", description = "작업지시 식별자가 중복되거나 저장 제약조건에 맞지 않습니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "409", value = "{\"status\":409,\"message\":\"작업지시 식별자가 중복되거나 저장 제약조건에 맞지 않습니다.\",\"workOrderNo\":null,\"executionStatus\":null}")}))
    })
    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(
            @Valid @RequestBody WorkOrderCreateRequest request
    ) {
        WorkOrder workOrder = workOrderService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(WorkOrderResponse.from(workOrder));
    }

    @Operation(summary = "계획정보 변경",
            description = "다섯 계획 필드를 모두 전달합니다. 실제 변경 시에만 version을 1 증가시키고 PENDING 및 오류 초기화 후 전송합니다. 무변경 요청은 버전·전송 상태를 유지하고 전송하지 않습니다. MES에서 생산 시작 후 주요 변경을 거부하면 ERP 계획은 유지하고 FAILED 및 MES 오류를 기록합니다. HTTP 200만으로 MES 동기화 성공을 판단하지 마세요.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderUpdateRequest.class),
                            examples = @ExampleObject(name = "request", value = "{\"plannedQuantity\":200,\"dueDate\":\"2026-09-12\",\"priority\":2,\"routingCode\":\"ROUTING-B\",\"routingRevision\":2}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ERP 처리 완료. MES 전송 결과는 mesSyncStatus 확인.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderResponse.class),
                            examples = {@ExampleObject(name = "success", value = "{\"id\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":2,\"mesSyncStatus\":\"SUCCESS\",\"mesSyncError\":null}"),
                                    @ExampleObject(name = "mesFailure", value = "{\"id\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":2,\"mesSyncStatus\":\"FAILED\",\"mesSyncError\":\"MES HTTP 409: 생산 시작 후 주요 계획은 변경할 수 없습니다.\"}")})),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 값 또는 중복 작업지시 번호",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "400", value = "{\"status\":400,\"message\":\"잘못된 요청 값 또는 중복 작업지시 번호\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "404", description = "작업지시를 찾을 수 없습니다: 999",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "404", value = "{\"status\":404,\"message\":\"작업지시를 찾을 수 없습니다: 999\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "409", description = "작업지시 식별자가 중복되거나 저장 제약조건에 맞지 않습니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "409", value = "{\"status\":409,\"message\":\"작업지시 식별자가 중복되거나 저장 제약조건에 맞지 않습니다.\",\"workOrderNo\":null,\"executionStatus\":null}")}))
    })
    @PutMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderUpdateRequest request
    ) {
        return ResponseEntity.ok(WorkOrderResponse.from(workOrderService.update(id, request)));
    }

    @Operation(summary = "실패한 MES 동기화 재전송",
            description = "FAILED 작업지시의 현재 계획과 현재 버전을 전송합니다. 버전은 증가하지 않습니다. PENDING 및 오류 초기화 후 전송하고, 동일 버전이 이미 적용된 IGNORED_SAME_VERSION도 SUCCESS입니다. IGNORED_OLD_VERSION 또는 응답 불일치는 FAILED입니다. 재실패 시 최신 오류로 갱신합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ERP 처리 완료. MES 전송 결과는 mesSyncStatus 확인.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkOrderResponse.class),
                            examples = {@ExampleObject(name = "success", value = "{\"id\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":2,\"mesSyncStatus\":\"SUCCESS\",\"mesSyncError\":null}"),
                                    @ExampleObject(name = "mesFailure", value = "{\"id\":1,\"workOrderNo\":\"WO-20260903-001\",\"version\":2,\"mesSyncStatus\":\"FAILED\",\"mesSyncError\":\"MES HTTP 409: 생산 시작 후 주요 계획은 변경할 수 없습니다.\"}")})),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 값 또는 중복 작업지시 번호",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "400", value = "{\"status\":400,\"message\":\"잘못된 요청 값 또는 중복 작업지시 번호\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "404", description = "작업지시를 찾을 수 없습니다: 999",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "404", value = "{\"status\":404,\"message\":\"작업지시를 찾을 수 없습니다: 999\",\"workOrderNo\":null,\"executionStatus\":null}")})),
            @ApiResponse(responseCode = "409", description = "FAILED 작업지시만 재전송할 수 있습니다. 현재 상태: SUCCESS",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
                            examples = {@ExampleObject(name = "409", value = "{\"status\":409,\"message\":\"FAILED 작업지시만 재전송할 수 있습니다. 현재 상태: SUCCESS\",\"workOrderNo\":null,\"executionStatus\":null}")}))
    })
    @PostMapping("/{id}/resend")
    public ResponseEntity<WorkOrderResponse> resend(@PathVariable Long id) {
        return ResponseEntity.ok(WorkOrderResponse.from(workOrderService.resend(id)));
    }
}
