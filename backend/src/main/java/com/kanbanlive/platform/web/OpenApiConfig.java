package com.kanbanlive.platform.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * Cabecera opcional X-Client-Id en las operaciones de escritura de tableros/listas/tarjetas: el servidor la
     * devuelve en el campo clientId del envelope de tiempo real (docs/contracts/board-events.md).
     */
    @Bean
    OperationCustomizer clientIdHeader() {
        return (operation, handlerMethod) -> {
            String pkg = handlerMethod.getBeanType().getPackageName();
            boolean boardScoped = pkg.startsWith("com.kanbanlive.boards") || pkg.startsWith("com.kanbanlive.lists")
                    || pkg.startsWith("com.kanbanlive.cards");
            boolean mutating = handlerMethod.hasMethodAnnotation(PostMapping.class)
                    || handlerMethod.hasMethodAnnotation(PatchMapping.class)
                    || handlerMethod.hasMethodAnnotation(DeleteMapping.class);
            if (boardScoped && mutating) {
                operation.addParametersItem(new HeaderParameter().name("X-Client-Id").required(false)
                        .description("Identificador de la instancia de cliente; se devuelve en el evento de tiempo "
                                + "real resultante para que el origen ignore su eco.")
                        .schema(new StringSchema().maxLength(64).pattern("^[A-Za-z0-9._:-]{1,64}$")));
            }
            return operation;
        };
    }
}
