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
    @Autowired MockMvc mvc;
    @Autowired WorkOrderRepository repository;

    @BeforeEach
    void setUp() { repository.deleteAll(); }

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
}
