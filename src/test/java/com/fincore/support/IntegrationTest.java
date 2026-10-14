package com.fincore.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.identity.application.ClientInfo;
import com.fincore.identity.application.SessionTokenIssuer;
import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración. Un único Postgres para toda la suite (se arranca una vez y
 * Testcontainers lo para al acabar) y la misma configuración en todas las clases, para que Spring
 * reutilice un solo contexto en vez de levantar uno por clase. Cada test crea sus propios usuarios
 * con ids aleatorios, así que compartir la base de datos no hace que se pisen.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("fincore.security.jwt.secret", () -> "secreto-de-tests-" + UUID.randomUUID() + UUID.randomUUID());
        // bcrypt con coste bajo: en tests no importa la seguridad del hash y el coste 12 los hace muy lentos.
        registry.add("fincore.security.bcrypt-strength", () -> "4");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AppUserJpaRepository userRepository;

    @Autowired
    private SessionTokenIssuer tokenIssuer;

    private final Map<UUID, String> tokens = new HashMap<>();

    protected static final String TEST_PASSWORD = "contraseña-de-test-larga";

    protected record Tokens(String email, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /** Registra un usuario nuevo por la API y hace login desde el dispositivo indicado. */
    protected Tokens registerAndLogin(String deviceName) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@fincore.test";
        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", TEST_PASSWORD))))
                .andExpect(status().isCreated());
        return login(email, TEST_PASSWORD, deviceName);
    }

    protected Tokens login(String email, String password, String deviceName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password, "deviceName", deviceName))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return new Tokens(email, json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    /** Cabecera Authorization para un usuario del test: abre una sesión de verdad y la reutiliza. */
    protected String bearer(UUID userId) {
        return tokens.computeIfAbsent(userId, id -> {
            AppUser user = userRepository.findById(id).orElseThrow();
            return "Bearer " + tokenIssuer.openSession(user, new ClientInfo("tests", "tests", "127.0.0.1")).accessToken();
        });
    }
}
