package com.kanbanlive.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kanbanlive.testsupport.SecuredApiIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * B4.1/B4.2 de extremo a extremo (REST + Postgres + seguridad activa): GET /api/boards/{id}/events?since=N.
 * max-gap = 5 para poder provocar el 410 con pocos eventos.
 */
@SpringBootTest(properties = "kanban.events.max-gap=5")
class EventsResyncIT extends SecuredApiIT {

    @Autowired
    JdbcTemplate jdbc;

    UUID board(String owner) throws Exception {
        return createBoard(owner, createWorkspace(owner, "ws"));
    }

    void renameBoard(String user, UUID board, int times) throws Exception {
        for (int v = 0; v < times; v++) {
            mvc.perform(patch("/api/boards/{id}", board).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"n" + v + "\",\"version\":" + v + "}")).andExpect(status().isOk());
        }
    }

    JsonNode events(String user, UUID board, String since, int expectedStatus) throws Exception {
        String json = mvc.perform(get("/api/boards/{id}/events", board).param("since", since).with(as(user)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void returnsTheEventsAfterSinceWithTheWsShapeAndTheLastSeq() throws Exception {
        String alice = user("alice");
        UUID board = board(alice);
        UUID list = createList(alice, board); // seq 1
        createCard(alice, list); // seq 2
        renameBoard(alice, board, 1); // seq 3

        JsonNode body = events(alice, board, "1", 200);

        assertThat(body.get("boardId").asText()).isEqualTo(board.toString());
        assertThat(body.get("since").asLong()).isEqualTo(1);
        assertThat(body.get("lastSeq").asLong()).isEqualTo(3);
        JsonNode evs = body.get("events");
        assertThat(evs).hasSize(2);
        assertThat(evs.get(0).get("seq").asLong()).isEqualTo(2);
        assertThat(evs.get(0).get("type").asText()).isEqualTo("card.created");
        assertThat(evs.get(0).get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(evs.get(0).get("occurredAt").asText()).isNotBlank();
        assertThat(evs.get(0).at("/payload/card/id").asText()).isNotBlank();
        assertThat(evs.get(1).get("seq").asLong()).isEqualTo(3);
        assertThat(evs.get(1).get("type").asText()).isEqualTo("board.updated");
    }

    @Test
    void sinceEqualToTheCurrentSeqReturnsAnEmptyList() throws Exception {
        String alice = user("alice");
        UUID board = board(alice);
        createList(alice, board);

        JsonNode body = events(alice, board, "1", 200);

        assertThat(body.get("events")).isEmpty();
        assertThat(body.get("lastSeq").asLong()).isEqualTo(1);
    }

    @Test
    void sinceAheadOfTheCurrentSeqIs400() throws Exception {
        String alice = user("alice");
        UUID board = board(alice);
        createList(alice, board);

        JsonNode problem = events(alice, board, "2", 400);

        assertThat(problem.get("code").asText()).isEqualTo("INVALID_SINCE");
        events(alice, board, "-1", 400);
    }

    @Test
    void missingOrMalformedSinceIs400() throws Exception {
        String alice = user("alice");
        UUID board = board(alice);
        mvc.perform(get("/api/boards/{id}/events", board).with(as(alice))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/boards/{id}/events", board).param("since", "abc").with(as(alice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreMissingEventsThanMaxGapIs410EventsGone() throws Exception {
        String alice = user("alice");
        UUID board = board(alice);
        renameBoard(alice, board, 7); // seq 1..7

        assertThat(events(alice, board, "2", 200).get("events")).hasSize(5); // justo max-gap
        JsonNode problem = events(alice, board, "1", 410);

        assertThat(problem.get("code").asText()).isEqualTo("EVENTS_GONE");
        assertThat(problem.get("status").asInt()).isEqualTo(410);
    }

    @Test
    void aPurgedLogIs410EvenWhenTheGapIsSmall() throws Exception {
        String alice = user("alice");
        UUID board = board(alice);
        renameBoard(alice, board, 4); // seq 1..4
        jdbc.update("delete from board_event where board_id = ? and seq <= 2", board); // purga por retencion

        assertThat(events(alice, board, "1", 410).get("code").asText()).isEqualTo("EVENTS_GONE");
        // since == primer retenido - 1: aun cubierto
        assertThat(events(alice, board, "2", 200).get("events")).hasSize(2);
    }

    @Test
    void onlyMembersCanReadTheEventsAndAnonymousIs401() throws Exception {
        String alice = user("alice");
        String mallory = user("mallory");
        UUID board = board(alice);
        createList(alice, board);

        mvc.perform(get("/api/boards/{id}/events", board).param("since", "0").with(as(mallory)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/boards/{id}/events", board).param("since", "0")).andExpect(status().isUnauthorized());
        // un VIEWER si puede (canView)
        String viewer = user("viewer");
        UUID ws = UUID.fromString(jdbc.queryForObject("select workspace_id::text from board where id = ?",
                String.class, board));
        invite(alice, ws, viewer, "VIEWER");
        mvc.perform(get("/api/boards/{id}/events", board).param("since", "0").with(as(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1));
    }
}
