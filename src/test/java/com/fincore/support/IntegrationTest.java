package com.fincore.support;

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

    /** Cabecera Authorization para un usuario del test: abre una sesión de verdad y la reutiliza. */
    protected String bearer(UUID userId) {
        return tokens.computeIfAbsent(userId, id -> {
            AppUser user = userRepository.findById(id).orElseThrow();
            return "Bearer " + tokenIssuer.openSession(user, new ClientInfo("tests", "tests", "127.0.0.1")).accessToken();
        });
    }
}
