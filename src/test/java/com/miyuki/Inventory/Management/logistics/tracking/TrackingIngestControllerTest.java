package com.miyuki.Inventory.Management.logistics.tracking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.miyuki.Inventory.Management.logistics.common.NotFoundException;
import com.miyuki.Inventory.Management.logistics.tracking.TrackingDtos.IngestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrackingIngestController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackingIngestControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PositionIngestionService service;

    private static String ping(String lat, String lng, String at) {
        return "{\"lat\":" + lat + ",\"lng\":" + lng + ",\"speedKmh\":32.5,\"heading\":90,\"recordedAt\":\"" + at + "\"}";
    }

    private static String body(Object vehicleId, String positionsJson) {
        return "{" + (vehicleId == null ? "" : "\"vehicleId\":" + vehicleId + ",") + "\"positions\":" + positionsJson + "}";
    }

    private void postBody(String json, int expectedStatus) throws Exception {
        mvc.perform(post("/api/tracking/positions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void validBatchReturns202WithCounts() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestResult(2, 1));

        mvc.perform(post("/api/tracking/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "[" + ping("12.97", "77.59", "2026-11-21T10:00:00Z") + ","
                                + ping("12.98", "77.60", "2026-11-21T10:00:05Z") + "]")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.rejected").value(1));
    }

    @Test
    void emptyBatchReturns400() throws Exception {
        postBody(body(1, "[]"), 400);
        verifyNoInteractions(service);
    }

    @Test
    void missingPositionsReturns400() throws Exception {
        postBody("{\"vehicleId\":1}", 400);
    }

    @Test
    void missingVehicleIdReturns400() throws Exception {
        postBody(body(null, "[" + ping("12.97", "77.59", "2026-11-21T10:00:00Z") + "]"), 400);
        verifyNoInteractions(service);
    }

    @Test
    void batchLargerThan500Returns400() throws Exception {
        String one = ping("12.97", "77.59", "2026-11-21T10:00:00Z");
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 501; i++) {
            many.append(i == 0 ? "" : ",").append(one);
        }
        many.append("]");

        postBody(body(1, many.toString()), 400);
        verifyNoInteractions(service);
    }

    @Test
    void exactly500IsAllowed() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestResult(500, 0));
        String one = ping("12.97", "77.59", "2026-11-21T10:00:00Z");
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 500; i++) {
            many.append(i == 0 ? "" : ",").append(one);
        }
        many.append("]");

        postBody(body(1, many.toString()), 202);
    }

    @Test
    void latitudeOutOfRangeReturns400() throws Exception {
        postBody(body(1, "[" + ping("91", "77.59", "2026-11-21T10:00:00Z") + "]"), 400);
        verifyNoInteractions(service);
    }

    @Test
    void longitudeOutOfRangeReturns400() throws Exception {
        postBody(body(1, "[" + ping("12.97", "-181", "2026-11-21T10:00:00Z") + "]"), 400);
    }

    @Test
    void missingTimestampReturns400() throws Exception {
        postBody(body(1, "[{\"lat\":12.9,\"lng\":77.5}]"), 400);
    }

    @Test
    void negativeSpeedReturns400() throws Exception {
        postBody(body(1, "[{\"lat\":12.9,\"lng\":77.5,\"speedKmh\":-5,\"recordedAt\":\"2026-11-21T10:00:00Z\"}]"), 400);
    }

    @Test
    void headingAbove360Returns400() throws Exception {
        postBody(body(1, "[{\"lat\":12.9,\"lng\":77.5,\"heading\":361,\"recordedAt\":\"2026-11-21T10:00:00Z\"}]"), 400);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        postBody("{not json", 400);
    }

    @Test
    void unknownVehicleReturns404() throws Exception {
        when(service.ingest(any())).thenThrow(new NotFoundException("Vehicle", 99L));

        postBody(body(99, "[" + ping("12.97", "77.59", "2026-11-21T10:00:00Z") + "]"), 404);
    }

    @Test
    void redisOutageReturns503WithRetryAfter() throws Exception {
        when(service.ingest(any())).thenThrow(new RedisConnectionFailureException("redis is down"));

        mvc.perform(post("/api/tracking/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1, "[" + ping("12.97", "77.59", "2026-11-21T10:00:00Z") + "]")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }
}
