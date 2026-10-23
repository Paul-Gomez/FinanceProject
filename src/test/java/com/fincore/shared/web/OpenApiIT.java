package com.fincore.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiIT extends IntegrationTest {

    @Test
    void apiDocsArePublicAndDeclareTheBearerScheme() throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode docs = objectMapper.readTree(body);
        assertThat(docs.at("/info/title").asText()).isEqualTo("FinCore API");
        assertThat(docs.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
        assertThat(docs.get("paths").has("/api/v1/auth/login")).isTrue();
        assertThat(docs.get("paths").has("/api/v1/transactions")).isTrue();
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
