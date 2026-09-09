package com.factoryflow.mes.workorder.controller;

import com.factoryflow.mes.workorder.entity.SourceSystem;
import com.factoryflow.mes.workorder.entity.WorkOrder;
import com.factoryflow.mes.workorder.entity.WorkOrderExecutionStatus;
import com.factoryflow.mes.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkOrderSyncApiIntegrationTest {
    @Autowired com.factoryflow.mes.workorder.repository.WorkOrderSyncHistoryRepository histories;
    @Autowired MockMvc mvc;
    @Autowired WorkOrderRepository repository;

    @BeforeEach
    void setUp() { histories.deleteAll(); repository.deleteAll(); }

    private String request(int version, int quantity, String routing, int revision) {
        return """
                {"sourceSystem":"ERP","eventId":"11111111-1111-1111-1111-111111111111",
                 "eventType":"WORK_ORDER_UPDATED","externalId":100,"workOrderNo":"WO-API-001",
                 "version":%d,"productCode":"PRODUCT-A001","plannedQuantity":%d,
                 "dueDate":"2026-09-10","priority":1,"routingCode":"%s","routingRevision":%d}
                """.formatted(version, quantity, routing, revision);
    }

    private void sync(String request, String result, int appliedVersion) throws Exception {
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(result))
                .andExpect(jsonPath("$.workOrderNo").value("WO-API-001"))
                .andExpect(jsonPath("$.appliedVersion").value(appliedVersion))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private java.util.List<com.factoryflow.mes.workorder.entity.WorkOrderSyncHistory> history() {
        return histories.findAll(org.springframework.data.domain.Sort.by("id"));
    }

    private WorkOrder stored() {
        return repository.findBySourceSystemAndExternalId(SourceSystem.ERP, 100L).orElseThrow();
    }

    private void state(Long id, String state) throws Exception {
        mvc.perform(patch("/api/work-orders/{id}/execution-status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"executionStatus\":\"" + state + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.executionStatus").value(state));
    }

    @Test
    void createLatestSameAndReorderedOldVersionPreserveOneCurrentRow() throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        Long id = stored().getId();
        assertThat(stored().getExecutionStatus()).isEqualTo(WorkOrderExecutionStatus.PLANNED);
        sync(request(3, 300, "ROUTING-B", 2), "UPDATED", 3);
        WorkOrder updated = stored();
        assertThat(updated.getPlannedQuantity()).isEqualTo(300);
        assertThat(updated.getRoutingCode()).isEqualTo("ROUTING-B");
        assertThat(updated.getRoutingRevision()).isEqualTo(2);
        sync(request(3, 999, "IGNORED", 9), "IGNORED_SAME_VERSION", 3);
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(2, 200, "ROUTING-A", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("IGNORED_OLD_VERSION"))
                .andExpect(jsonPath("$.receivedVersion").value(2)).andExpect(jsonPath("$.appliedVersion").value(3));
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stored().getId()).isEqualTo(id);
        assertThat(stored().getPlannedQuantity()).isEqualTo(300);
        assertThat(stored().getSourceVersion()).isEqualTo(3);
        assertThat(stored().getRoutingCode()).isEqualTo("ROUTING-B");
        assertThat(stored().getUpdatedAt()).isEqualTo(updated.getUpdatedAt());
        var rows = history();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(a -> a.getResult().name())
                .containsExactly("CREATED", "UPDATED", "IGNORED_SAME_VERSION", "IGNORED_OLD_VERSION");
        assertThat(rows).extracting(a -> a.getPreviousVersion()).containsExactly(null, 1, 3, 3);
        assertThat(rows).extracting(a -> a.getAppliedVersion()).containsExactly(1, 3, 3, 3);
        assertThat(rows).extracting(a -> a.getReceivedVersion()).containsExactly(1, 3, 3, 2);
        assertThat(rows).allSatisfy(a -> {
            assertThat(a.getEventId().toString()).isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(a.getSourceSystem()).isEqualTo(SourceSystem.ERP);
            assertThat(a.getExternalId()).isEqualTo(100L);
            assertThat(a.getMesWorkOrderId()).isEqualTo(id);
            assertThat(a.getCompletedAt()).isAfterOrEqualTo(a.getReceivedAt());
        });
    }

    @ParameterizedTest
    @CsvSource({"200,ROUTING-A,1", "100,ROUTING-B,1", "100,ROUTING-A,2"})
    void eachMajorChangeReturns409AndLeavesAllMesDataUntouched(int quantity, String routing, int revision) throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        state(stored().getId(), "IN_PROGRESS");
        WorkOrder before = stored();
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(2, quantity, routing, revision).replace("2026-09-10", "2026-09-12")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.workOrderNo").value("WO-API-001"))
                .andExpect(jsonPath("$.executionStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("생산 시작 후")));
        WorkOrder after = stored();
        assertThat(after.getPlannedQuantity()).isEqualTo(100);
        assertThat(after.getRoutingCode()).isEqualTo("ROUTING-A");
        assertThat(after.getRoutingRevision()).isEqualTo(1);
        assertThat(after.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(after.getSourceVersion()).isEqualTo(1);
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        // 테스트를 외부 트랜잭션으로 감싸지 않고, 409 이후 별도 DB 읽기로 보존을 확인한다.
        var rows = history();
        assertThat(rows).hasSize(2);
        var rejected = rows.get(1);
        assertThat(rejected.getResult().name()).isEqualTo("REJECTED_PRODUCTION_STARTED");
        assertThat(rejected.getReceivedVersion()).isEqualTo(2);
        assertThat(rejected.getPreviousVersion()).isEqualTo(1);
        assertThat(rejected.getAppliedVersion()).isEqualTo(1);
        assertThat(rejected.getMesWorkOrderId()).isEqualTo(before.getId());
        assertThat(rejected.getMessage()).contains("생산 시작 후");
        sync(request(1, 999, "IGNORED", 9), "IGNORED_SAME_VERSION", 1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void schedulingChangesAllowedAfterStartButProductionCannotBeReopened() throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        Long id = stored().getId();
        state(id, "READY");
        state(id, "IN_PROGRESS");
        sync(request(2, 100, "ROUTING-A", 1).replace("2026-09-10", "2026-09-12")
                .replace("\"priority\":1", "\"priority\":2"), "UPDATED", 2);
        assertThat(stored().getPriority()).isEqualTo(2);
        assertThat(stored().getDueDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        mvc.perform(patch("/api/work-orders/{id}/execution-status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionStatus\":\"PLANNED\"}"))
                .andExpect(status().isConflict());
        state(id, "COMPLETED");
        state(id, "COMPLETED");
        mvc.perform(patch("/api/work-orders/{id}/execution-status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(3, 200, "ROUTING-A", 1)))
                .andExpect(status().isConflict());
        assertThat(stored().getSourceVersion()).isEqualTo(2);
    }

    @Test
    void invalidRequestsUnsupportedSourceAndMissingOrderHaveClearErrors() throws Exception {
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, 0, "ROUTING-A", 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, 100, "ROUTING-A", 1).replace("\"ERP\"", "\"APS\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/work-orders/999/execution-status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/work-orders/999/execution-status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
        assertThat(history()).hasSize(1); // Validation 실패는 제외, 서비스 도달 APS 거부만 기록
        assertThat(history().get(0).getResult().name()).isEqualTo("REJECTED_UNSUPPORTED_SOURCE");
    }

    @Test
    void sameNumberFromDifferentExternalIdConflictsWithoutCreatingAnotherRow() throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, 100, "ROUTING-A", 1).replace("\"externalId\":100", "\"externalId\":101")))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void openApiPublishesRequestExamplesAndErrorContracts() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/work-orders/sync'].post.responses['200'].content['application/json'].examples.IGNORED_SAME_VERSION").exists())
                .andExpect(jsonPath("$.paths['/api/work-orders/sync'].post.responses['200'].content['application/json'].examples.IGNORED_OLD_VERSION").exists())
                .andExpect(jsonPath("$.paths['/api/work-orders/sync'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/work-orders/{id}/execution-status'].patch.requestBody").exists());
    }

    @ParameterizedTest
    @CsvSource({"workOrderNo,WO-API-001,RENAMED", "productCode,PRODUCT-A001,OTHER"})
    void identityChangesAreRejectedAndRecordedWithoutChangingPlan(String field, String from, String to) throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        WorkOrder before = stored();
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                        .content(request(2, 200, "ROUTING-B", 2).replace(from, to)))
                .andExpect(status().isConflict());
        assertThat(history()).hasSize(2);
        var row = history().get(1);
        assertThat(row.getResult().name()).isEqualTo("REJECTED_IDENTITY_CONFLICT");
        assertThat(row.getPreviousVersion()).isEqualTo(1);
        assertThat(row.getAppliedVersion()).isEqualTo(1);
        assertThat(row.getMessage()).contains("번호와 품목");
        assertThat(stored()).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void rejectedCreationWithoutMesOrderCanBeTracedByEvent() throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        String event = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                .content(request(1, 100, "ROUTING-A", 1).replace("\"externalId\":100", "\"externalId\":101")
                        .replace("11111111-1111-1111-1111-111111111111", event)))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
        assertThat(history()).hasSize(2);
        var row = history().get(1);
        assertThat(row.getMesWorkOrderId()).isNull();
        assertThat(row.getExternalId()).isEqualTo(101L);
        assertThat(row.getPreviousVersion()).isNull();
        assertThat(row.getAppliedVersion()).isNull();
        mvc.perform(get("/api/sync-history/events/{id}", event))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].result").value("REJECTED_IDENTITY_CONFLICT"))
                .andExpect(jsonPath("$.content[0].externalId").value(101))
                .andExpect(jsonPath("$.content[0].mesWorkOrderId").isEmpty());
        // 나중에 동일 원천 작업지시가 다른 번호로 생성되면 생성 전 거부 이력도 함께 조회한다.
        mvc.perform(post("/api/work-orders/sync").contentType(MediaType.APPLICATION_JSON)
                .content(request(1, 100, "ROUTING-A", 1).replace("\"externalId\":100", "\"externalId\":101")
                        .replace("WO-API-001", "WO-NEW")))
                .andExpect(status().isOk());
        Long newId = repository.findBySourceSystemAndExternalId(SourceSystem.ERP, 101L).orElseThrow().getId();
        mvc.perform(get("/api/work-orders/{id}/sync-history", newId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void historyAndRepeatedEventQueriesAreNewestFirstAndPaged() throws Exception {
        sync(request(1, 100, "ROUTING-A", 1), "CREATED", 1);
        sync(request(2, 200, "ROUTING-A", 1), "UPDATED", 2);
        sync(request(2, 200, "ROUTING-A", 1), "IGNORED_SAME_VERSION", 2);
        Long id = stored().getId();
        var rows = history();
        mvc.perform(get("/api/work-orders/{id}/sync-history", id).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(rows.get(2).getId()))
                .andExpect(jsonPath("$.content[1].id").value(rows.get(1).getId()))
                .andExpect(jsonPath("$.content[0].mesWorkOrderId").value(id))
                .andExpect(jsonPath("$.content[0].externalId").value(100))
                .andExpect(jsonPath("$.content[0].workOrder").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/work-orders/{id}/sync-history", id).param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(rows.get(0).getId()));
        mvc.perform(get("/api/sync-history/events/{id}", rows.get(0).getEventId()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].result").value("IGNORED_SAME_VERSION"));
        mvc.perform(get("/api/sync-history/events/{id}", rows.get(0).getEventId()).param("page", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void missingHistoryAndInvalidPagingHaveClearErrors() throws Exception {
        mvc.perform(get("/api/work-orders/999/sync-history")).andExpect(status().isNotFound());
        mvc.perform(get("/api/sync-history/events/{id}", java.util.UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/sync-history/events/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/work-orders/999/sync-history").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/work-orders/999/sync-history").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/work-orders/999/sync-history").param("size", "101")).andExpect(status().isBadRequest());
        var order = repository.save(WorkOrder.createFrom(new com.factoryflow.mes.workorder.dto.WorkOrderSyncRequest(
                SourceSystem.ERP, java.util.UUID.randomUUID(), "WORK_ORDER_CREATED", 100L, "EMPTY", 1,
                "P", 1, LocalDate.of(2026, 9, 10), 0, null, 1)));
        mvc.perform(get("/api/work-orders/{id}/sync-history", order.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void openApiDocumentsHistoryPagingErrorsAndTraceFields() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/work-orders/{id}/sync-history'].get.parameters[?(@.name == 'page')]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/sync-history/events/{eventId}'].get.responses['200'].content['application/json'].examples.history").exists())
                .andExpect(jsonPath("$.paths['/api/sync-history/events/{eventId}'].get.responses['404'].content['application/json'].examples").exists())
                .andExpect(jsonPath("$.components.schemas.WorkOrderSyncHistoryResponse.properties.mesWorkOrderId.description").exists())
                .andExpect(jsonPath("$.components.schemas.WorkOrderSyncHistoryResponse.properties.externalId.description").exists())
                .andExpect(jsonPath("$.components.schemas.WorkOrderSyncHistoryResponse.properties.result.enum").isArray());
    }
}
