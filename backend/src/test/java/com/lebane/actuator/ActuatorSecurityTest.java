package com.lebane.actuator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exposición segura de Actuator (no requiere Docker: usa el perfil "nodb").
 *
 * <p>{@code @AutoConfigureObservability}: Spring Boot desactiva los exporters de métricas en tests; sin él no
 * existe el registry de Prometheus y {@code /actuator/prometheus} respondería 404.
 */
@SpringBootTest
@AutoConfigureObservability(tracing = false)
@AutoConfigureMockMvc
@ActiveProfiles("nodb")
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infoIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/metrics", "/actuator/prometheus"})
    void protectedEndpointsRequireAuthentication(String path) throws Exception {
        mockMvc.perform(get(path).header("X-Request-Id", "sec-req-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("sec-req-1"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void protectedEndpointsRejectWrongCredentials() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("probe", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsAvailableWithCredentials() throws Exception {
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("probe", "probe-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    void prometheusAvailableWithCredentials() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(httpBasic("probe", "probe-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/mappings",
            "/actuator/threaddump", "/actuator/heapdump", "/actuator/loggers", "/actuator/shutdown"})
    void sensitiveEndpointsAreNotExposed(String path) throws Exception {
        mockMvc.perform(get(path).with(httpBasic("probe", "probe-secret")))
                .andExpect(status().isNotFound());
    }
}
