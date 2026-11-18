package com.kanbanlive.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kanbanlive.testsupport.SecuredApiIT;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * B2.5 - DoD de la Fase 2: un usuario del workspace A no lee ni modifica nada del B. Parametrizado sobre TODOS los
 * endpoints con recurso: el usuario de B los invoca con ids de A y siempre recibe 403 (tambien para ids
 * inexistentes: no se distingue "no existe" de "no es tuyo", para no filtrar existencia entre workspaces).
 */
class TenantIsolationIT extends SecuredApiIT {

    /** Ids de los dos workspaces de prueba. */
    record Fixture(String userA, String userB, UUID wsA, UUID boardA, UUID listA, UUID cardA, UUID wsB, UUID boardB,
            UUID listB, UUID cardB) {
    }

    /** Un endpoint del API: metodo, ruta y cuerpo en funcion de los ids del fixture. */
    record Endpoint(HttpMethod method, Function<Fixture, String> path, Function<Fixture, String> body) {

        MockHttpServletRequestBuilder request(Fixture f) {
            MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(method, path.apply(f));
            if (body != null) {
                b.contentType(MediaType.APPLICATION_JSON).content(body.apply(f));
            }
            return b;
        }
    }

    Fixture f;

    @BeforeEach
    void fixture() throws Exception {
        String a = user("alice");
        String b = user("bob");
        UUID wsA = createWorkspace(a, "A");
        UUID boardA = createBoard(a, wsA);
        UUID listA = createList(a, boardA);
        UUID cardA = createCard(a, listA);
        UUID wsB = createWorkspace(b, "B");
        UUID boardB = createBoard(b, wsB);
        UUID listB = createList(b, boardB);
        UUID cardB = createCard(b, listB);
        f = new Fixture(a, b, wsA, boardA, listA, cardA, wsB, boardB, listB, cardB);
    }

    static Endpoint ep(HttpMethod m, Function<Fixture, String> path, Function<Fixture, String> body) {
        return new Endpoint(m, path, body);
    }

    /** Todos los endpoints con recurso, escritos como los usa el usuario A (todo apunta a recursos de A). */
    static Stream<Arguments> endpointsOfA() {
        return Stream.of(
                Arguments.of(Named.of("GET workspace", ep(HttpMethod.GET, x -> "/api/workspaces/" + x.wsA, null))),
                Arguments.of(Named.of("GET boards of workspace",
                        ep(HttpMethod.GET, x -> "/api/workspaces/" + x.wsA + "/boards", null))),
                Arguments.of(Named.of("POST board",
                        ep(HttpMethod.POST, x -> "/api/workspaces/" + x.wsA + "/boards", x -> "{\"name\":\"n\"}"))),
                Arguments.of(Named.of("GET board", ep(HttpMethod.GET, x -> "/api/boards/" + x.boardA, null))),
                Arguments.of(Named.of("PATCH board", ep(HttpMethod.PATCH, x -> "/api/boards/" + x.boardA,
                        x -> "{\"name\":\"n\",\"version\":0}"))),
                Arguments.of(Named.of("POST list",
                        ep(HttpMethod.POST, x -> "/api/boards/" + x.boardA + "/lists", x -> "{\"name\":\"n\"}"))),
                Arguments.of(Named.of("PATCH list", ep(HttpMethod.PATCH, x -> "/api/lists/" + x.listA,
                        x -> "{\"name\":\"n\",\"version\":0}"))),
                Arguments.of(Named.of("POST move list",
                        ep(HttpMethod.POST, x -> "/api/lists/" + x.listA + "/move", x -> "{}"))),
                Arguments.of(Named.of("POST card",
                        ep(HttpMethod.POST, x -> "/api/lists/" + x.listA + "/cards", x -> "{\"title\":\"t\"}"))),
                Arguments.of(Named.of("PATCH card", ep(HttpMethod.PATCH, x -> "/api/cards/" + x.cardA,
                        x -> "{\"title\":\"t\",\"version\":0}"))),
                Arguments.of(Named.of("POST move card", ep(HttpMethod.POST, x -> "/api/cards/" + x.cardA + "/move",
                        x -> "{\"listId\":\"" + x.listA + "\"}"))),
                Arguments.of(Named.of("DELETE card", ep(HttpMethod.DELETE, x -> "/api/cards/" + x.cardA, null))),
                Arguments.of(Named.of("POST invite member", ep(HttpMethod.POST,
                        x -> "/api/workspaces/" + x.wsA + "/members",
                        x -> "{\"userId\":\"" + x.userB + "\",\"role\":\"OWNER\"}"))),
                Arguments.of(Named.of("PATCH member role", ep(HttpMethod.PATCH,
                        x -> "/api/workspaces/" + x.wsA + "/members/" + x.userA, x -> "{\"role\":\"VIEWER\"}"))));
    }

    @ParameterizedTest
    @MethodSource("endpointsOfA")
    void userOfBCannotReachResourcesOfA(Endpoint endpoint) throws Exception {
        mvc.perform(endpoint.request(f).with(as(f.userB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @MethodSource("endpointsOfA")
    void withoutTokenIs401(Endpoint endpoint) throws Exception {
        mvc.perform(endpoint.request(f)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("endpointsOfA")
    void nonexistentResourceIsIndistinguishableFromForeignOne(Endpoint endpoint) throws Exception {
        // Mismo endpoint pero con todos los ids de A sustituidos por UUID inexistentes.
        Fixture ghost = new Fixture(f.userA, f.userB, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), f.wsB, f.boardB, f.listB, f.cardB);
        mvc.perform(endpoint.request(ghost).with(as(f.userB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void cannotMoveOwnCardIntoListOfAnotherWorkspace() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/cards/{id}/move", f.cardB).with(as(f.userB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listId\":\"" + f.listA + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerOfAStillWorksOnItsOwnEndpoints() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/boards/{id}", f.boardA).with(as(f.userA)))
                .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.post("/api/cards/{id}/move", f.cardA).with(as(f.userA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listId\":\"" + f.listA + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void workspaceListingOnlyShowsOwnWorkspaces() throws Exception {
        String json = mvc.perform(MockMvcRequestBuilders.get("/api/workspaces").with(as(f.userB)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = mapper.readTree(json);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(f.wsB.toString());
    }

    @Test
    void boardListingOfForeignWorkspaceIsForbiddenAndOwnIsFiltered() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/workspaces/{id}/boards", f.wsB).with(as(f.userB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
