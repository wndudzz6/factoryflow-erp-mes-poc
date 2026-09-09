package com.factoryflow.erp.workorder.controller;

import com.factoryflow.erp.workorder.entity.MesSyncStatus;
import com.factoryflow.erp.workorder.entity.WorkOrder;
import com.factoryflow.erp.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WorkOrderApiIntegrationTest.MesStubConfig.class)
class WorkOrderApiIntegrationTest {
    private static final String CREATE = """
            {"workOrderNo":"WO-API-001","productCode":"PRODUCT-A001","plannedQuantity":100,
             "dueDate":"2026-09-10","priority":1,"routingCode":"ROUTING-A","routingRevision":1}
            """;
    private static final String UPDATE = """
            {"plannedQuantity":200,"dueDate":"2026-09-12","priority":2,
             "routingCode":"ROUTING-B","routingRevision":2}
            """;

    @Autowired MockMvc mvc;
    @Autowired WorkOrderRepository repository;
    @Autowired MockRestServiceServer mes;

    @TestConfiguration(proxyBeanMethods = false)
    static class MesStubConfig {
        private final RestClient.Builder builder = RestClient.builder().baseUrl("http://mes.test");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean MockRestServiceServer mesServer() { return server; }
        @Bean @Primary RestClient testMesRestClient() { return builder.build(); }
    }

    @BeforeEach
    void setUp() {
        mes.reset();
        repository.deleteAll();
    }

    @AfterEach
    void verifyRequests() { mes.verify(); }

    private ResponseActions expectSync(int version, int quantity) {
        return mes.expect(requestTo("http://mes.test/api/work-orders/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.sourceSystem").value("ERP"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.externalId").isNumber())
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.eventId").isNotEmpty())
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.workOrderNo").value("WO-API-001"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.version").value(version))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.plannedQuantity").value(quantity));
    }

    private String response(int received, int applied, String result) {
        return """
                {"id":11,"workOrderNo":"WO-API-001","receivedVersion":%d,
                 "appliedVersion":%d,"result":"%s","message":"processed"}
                """.formatted(received, applied, result);
    }

    private Long create() throws Exception {
        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1));
        return repository.findByWorkOrderNo("WO-API-001").orElseThrow().getId();
    }

    private WorkOrder stored(Long id) { return repository.findById(id).orElseThrow(); }

    @Test
    void createThenUpdateSendsCurrentPlanAndPersistsSuccess() throws Exception {
        expectSync(1, 100).andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.eventType")
                .value("WORK_ORDER_CREATED")).andRespond(withSuccess(response(1, 1, "CREATED"), MediaType.APPLICATION_JSON));
        Long id = create();
        assertThat(stored(id).getMesSyncStatus()).isEqualTo(MesSyncStatus.SUCCESS);
        mes.verify();
        mes.reset();
        expectSync(2, 200)
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.eventType").value("WORK_ORDER_UPDATED"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.dueDate").value("2026-09-12"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.routingCode").value("ROUTING-B"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.routingRevision").value(2))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.priority").value(2))
                .andRespond(withSuccess(response(2, 2, "UPDATED"), MediaType.APPLICATION_JSON));

        mvc.perform(put("/api/work-orders/{id}", id).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.mesSyncStatus").value("SUCCESS"));
        WorkOrder saved = stored(id);
        assertThat(saved.getPlannedQuantity()).isEqualTo(200);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(saved.getPriority()).isEqualTo(2);
        assertThat(saved.getRoutingCode()).isEqualTo("ROUTING-B");
        assertThat(saved.getRoutingRevision()).isEqualTo(2);
        assertThat(saved.getVersion()).isEqualTo(2);
        mes.verify();
        mes.reset();
        // No expectation: any additional MES call fails this test.
        mvc.perform(put("/api/work-orders/{id}", id).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "IGNORED_SAME_VERSION"})
    void lostResponseThenResendSucceedsWithoutNewVersion(String result) throws Exception {
        expectSync(1, 100).andRespond(withException(new IOException("response lost")));
        Long id = create();
        assertThat(stored(id).getMesSyncStatus()).isEqualTo(MesSyncStatus.FAILED);
        assertThat(stored(id).getMesSyncError()).contains("response lost");
        mes.verify();
        mes.reset();
        expectSync(1, 100).andRespond(withSuccess(response(1, 1, result), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/work-orders/{id}/resend", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.mesSyncStatus").value("SUCCESS"));
        assertThat(stored(id).getVersion()).isEqualTo(1);
        assertThat(stored(id).getMesSyncError()).isNull();
    }

    @Test
    void mesConflictPersistsFailedUpdateAndResendRefreshesError() throws Exception {
        expectSync(1, 100).andRespond(withSuccess(response(1, 1, "CREATED"), MediaType.APPLICATION_JSON));
        Long id = create();
        mes.verify();
        mes.reset();
        String conflict = """
                {"status":409,"workOrderNo":"WO-API-001","executionStatus":"IN_PROGRESS",
                 "message":"Production started: quantity and routing cannot change"}
                """;
        expectSync(2, 200).andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(conflict));
        mvc.perform(put("/api/work-orders/{id}", id).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mesSyncStatus").value("FAILED"));
        assertThat(stored(id).getVersion()).isEqualTo(2);
        assertThat(stored(id).getPlannedQuantity()).isEqualTo(200);
        assertThat(stored(id).getMesSyncError()).contains("409", "IN_PROGRESS", "Production started");
        mes.verify();
        mes.reset();
        expectSync(2, 200).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("latest outage"));
        mvc.perform(post("/api/work-orders/{id}/resend", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mesSyncStatus").value("FAILED"));
        assertThat(stored(id).getMesSyncError()).contains("latest outage").doesNotContain("Production started");
        mes.verify();
        mes.reset();
        expectSync(2, 200).andRespond(withSuccess(response(2, 2, "IGNORED_SAME_VERSION"), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/work-orders/{id}/resend", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.mesSyncStatus").value("SUCCESS"));
        assertThat(stored(id).getVersion()).isEqualTo(2);
    }

    @Test
    void oldVersionResponseIsNotReportedAsSynchronized() throws Exception {
        expectSync(1, 100).andRespond(withSuccess(response(1, 3, "IGNORED_OLD_VERSION"), MediaType.APPLICATION_JSON));
        Long id = create();
        assertThat(stored(id).getMesSyncStatus()).isEqualTo(MesSyncStatus.FAILED);
        assertThat(stored(id).getMesSyncError()).contains("IGNORED_OLD_VERSION");
    }

    @Test
    void emptySuccessBodyIsFailure() throws Exception {
        expectSync(1, 100).andRespond(withSuccess());
        Long id = create();
        assertThat(stored(id).getMesSyncStatus()).isEqualTo(MesSyncStatus.FAILED);
    }

    @Test
    void invalidRequestsAndIneligibleResendHaveClearHttpErrors() throws Exception {
        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE.replace("\"plannedQuantity\":100", "\"plannedQuantity\":0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        assertThat(repository.count()).isZero();
        mvc.perform(put("/api/work-orders/999").contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/work-orders/999/resend")).andExpect(status().isNotFound());
        expectSync(1, 100).andRespond(withSuccess(response(1, 1, "CREATED"), MediaType.APPLICATION_JSON));
        Long id = create();
        mvc.perform(post("/api/work-orders/{id}/resend", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SUCCESS")));
        mvc.perform(post("/api/work-orders").contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/work-orders/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE.replace("\"plannedQuantity\":200", "\"plannedQuantity\":-1")))
                .andExpect(status().isBadRequest());
        assertThat(stored(id).getVersion()).isEqualTo(1);
    }

    @Test
    void openApiPublishesRequestExamplesAndErrorContracts() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/work-orders'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/work-orders/{id}'].put.requestBody.content['application/json'].examples.request").exists())
                .andExpect(jsonPath("$.paths['/api/work-orders/{id}'].put.responses['200'].content['application/json'].examples.mesFailure").exists())
                .andExpect(jsonPath("$.paths['/api/work-orders/{id}/resend'].post.responses['409']").exists());
    }
}
