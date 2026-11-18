package com.kanbanlive.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base de los tests de seguridad end-to-end (contexto completo + Postgres, seguridad ACTIVA). Cada "usuario" es un
 * JWT simulado cuyo claim sub es el nombre; el API real crea workspaces/tableros/listas/tarjetas, de modo que el
 * creador es OWNER (igual que en produccion).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class SecuredApiIT extends PostgresIT {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper mapper;

    /** Cada test usa usuarios con sufijo unico para no depender del orden ni de datos de otros tests. */
    protected final String suffix = UUID.randomUUID().toString().substring(0, 8);

    protected String user(String name) {
        return name + "-" + suffix;
    }

    protected RequestPostProcessor as(String userId) {
        return jwt().jwt(j -> j.subject(userId));
    }

    protected UUID createWorkspace(String userId, String name) throws Exception {
        return id(post("/api/workspaces"), userId, "{\"name\":\"" + name + "\"}");
    }

    protected UUID createBoard(String userId, UUID workspaceId) throws Exception {
        return id(post("/api/workspaces/{id}/boards", workspaceId), userId, "{\"name\":\"board\"}");
    }

    protected UUID createList(String userId, UUID boardId) throws Exception {
        return id(post("/api/boards/{id}/lists", boardId), userId, "{\"name\":\"list\"}");
    }

    protected UUID createCard(String userId, UUID listId) throws Exception {
        return id(post("/api/lists/{id}/cards", listId), userId, "{\"title\":\"card\"}");
    }

    protected void invite(String ownerId, UUID workspaceId, String userId, String role) throws Exception {
        mvc.perform(post("/api/workspaces/{id}/members", workspaceId).with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated());
    }

    private UUID id(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String userId, String body) throws Exception {
        String json = mvc.perform(request.with(as(userId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(json);
        return UUID.fromString(node.get("id").asText());
    }
}
