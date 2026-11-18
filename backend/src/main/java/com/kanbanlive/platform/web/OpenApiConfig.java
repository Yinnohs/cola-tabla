package com.kanbanlive.platform.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del contrato OpenAPI (springdoc). La especificacion se genera desde los controllers de los adapters web;
 * {@code docs/contracts/openapi.json} es la copia versionada que consume el cliente TypeScript del frontend.
 */
@Configuration
class OpenApiConfig {

    static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI kanbanOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Kanban Live API").version("0.1.0")
                        .description("REST de Workspace -> Board -> List -> Card. Errores: RFC 7807 con `code` estable."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
