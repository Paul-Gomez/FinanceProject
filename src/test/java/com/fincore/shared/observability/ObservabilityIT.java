package com.fincore.shared.observability;

import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring Boot apaga la exportación de métricas en los tests si no se pide expresamente.
@AutoConfigureObservability
class ObservabilityIT extends IntegrationTest {

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        String id = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotBlank();
    }

    @Test
    void aValidIncomingRequestIdIsKeptAndAnInvalidOneIsReplaced() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health").header(RequestIdFilter.HEADER, "abc-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "abc-123"));

        String replaced = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health")
                        .header(RequestIdFilter.HEADER, "malo\nINFO falso"))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(replaced).doesNotContain("\n").doesNotContain("malo");
    }

    @Test
    void prometheusExposesBusinessMetrics() throws Exception {
        registerAndLogin("metricas");
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/prometheus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("fincore_business_events_total").contains("action=\"REGISTER\"");
    }
}
