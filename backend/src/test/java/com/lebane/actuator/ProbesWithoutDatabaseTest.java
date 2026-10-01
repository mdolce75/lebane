package com.lebane.actuator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PostgreSQL inaccesible (perfil "nodb", no requiere Docker):
 * liveness debe seguir UP (no depende de servicios externos) y readiness debe responder 503.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("nodb")
class ProbesWithoutDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void livenessIsUpEvenIfDatabaseIsDown() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void readinessIs503WhenDatabaseIsDown() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    }

    @Test
    void aggregatedHealthDoesNotLeakDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void probesReturnRequestIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness").header("X-Request-Id", "probe-req-1"))
                .andExpect(header().string("X-Request-Id", "probe-req-1"));
    }
}
