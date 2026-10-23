package com.fincore.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API en /swagger-ui.html (y el JSON en /v3/api-docs).
 * Se declara el esquema "bearer" para que Swagger UI tenga el botón "Authorize": se pega el
 * access token del login y las peticiones de prueba salen con la cabecera Authorization.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer";

    @Bean
    OpenAPI fincoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinCore API")
                        .version("v1")
                        .description("""
                                API de finanzas personales: cuentas, movimientos, transferencias, presupuestos, \
                                objetivos, suscripciones, facturas, importación CSV y analítica.

                                Para probarla: POST /api/v1/auth/register, después POST /api/v1/auth/login, \
                                copia el accessToken y pégalo en "Authorize".""")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Casi todo exige token. Los endpoints públicos (registro, login...) también lo
                // muestran con candado, pero funcionan igual sin él.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
