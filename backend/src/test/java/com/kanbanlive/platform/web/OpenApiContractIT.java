package com.kanbanlive.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kanbanlive.testsupport.PostgresIT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B1.9: el contrato OpenAPI publicado lista los endpoints principales y coincide con la copia versionada
 * {@code docs/contracts/openapi.json} (base del cliente TS, F1.2). Para regenerarla tras cambiar la API:
 * {@code ./mvnw verify -Dit.test=OpenApiContractIT -Dopenapi.export=true -Dtest=NoSuch* -Dsurefire.failIfNoSpecifiedTests=false}
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractIT extends PostgresIT {

    /** El working dir de failsafe es backend/. */
    static final Path CONTRACT = Path.of("..", "docs", "contracts", "openapi.json");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    JsonNode apiDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    @Test
    void apiDocsListTheMainPathsWithBearerSecurity() throws Exception {
        JsonNode docs = apiDocs();

        assertThat(docs.get("paths").fieldNames()).toIterable().contains(
                "/api/workspaces",
                "/api/workspaces/{workspaceId}/boards",
                "/api/boards/{boardId}",
                "/api/boards/{boardId}/lists",
                "/api/lists/{listId}",
                "/api/lists/{listId}/move",
                "/api/lists/{listId}/cards",
                "/api/cards/{cardId}",
                "/api/cards/{cardId}/move");
        assertThat(docs.at("/components/securitySchemes/bearer-jwt/scheme").asText()).isEqualTo("bearer");
        assertThat(docs.at("/components/securitySchemes/bearer-jwt/bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    void swaggerUiIsServedWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void committedContractMatchesTheGeneratedOne() throws Exception {
        JsonNode generated = apiDocs();
        // servers depende de la URL de la peticion (MockMvc) y no forma parte del contrato.
        ((com.fasterxml.jackson.databind.node.ObjectNode) generated).remove("servers");

        if (Boolean.getBoolean("openapi.export")) {
            ObjectMapper pretty = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            Files.writeString(CONTRACT, pretty.writeValueAsString(generated) + "\n", StandardCharsets.UTF_8);
            return;
        }
        assertThat(CONTRACT).as("docs/contracts/openapi.json (regenerar con -Dopenapi.export=true)").exists();
        JsonNode committed = mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(generated).as("la API cambio: regenera docs/contracts/openapi.json").isEqualTo(committed);
    }
}
