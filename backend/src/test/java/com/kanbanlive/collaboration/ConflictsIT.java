package com.kanbanlive.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kanbanlive.testsupport.SecuredApiIT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * B4.4 (ADR-0005): editar con version obsoleta => 409 STALE_VERSION con el estado actual en {@code current} (los
 * tres recursos editables); mover es last-write-wins y nunca devuelve 409.
 */
class ConflictsIT extends SecuredApiIT {

    @Autowired
    JdbcTemplate jdbc;

    JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String user,
            String body, int expected) throws Exception {
        String json = mvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.isBlank() ? null : mapper.readTree(json);
    }

    @Test
    void concurrentCardEditIs409WithTheCurrentCardInTheBody() throws Exception {
        String alice = user("alice");
        UUID board = createBoard(alice, createWorkspace(alice, "ws"));
        UUID card = createCard(alice, createList(alice, board));

        call(patch("/api/cards/{id}", card), alice, "{\"title\":\"A wins\",\"description\":\"d\",\"version\":0}", 200);
        JsonNode problem = call(patch("/api/cards/{id}", card), alice, "{\"title\":\"B loses\",\"version\":0}", 409);

        assertThat(problem.get("code").asText()).isEqualTo("STALE_VERSION");
        assertThat(problem.get("status").asInt()).isEqualTo(409);
        JsonNode current = problem.get("current");
        assertThat(current.get("id").asText()).isEqualTo(card.toString());
        assertThat(current.get("title").asText()).isEqualTo("A wins");
        assertThat(current.get("description").asText()).isEqualTo("d");
        assertThat(current.get("version").asLong()).isEqualTo(1);
        assertThat(current.has("listId")).isTrue();
        assertThat(current.has("position")).isTrue();
        assertThat(jdbc.queryForObject("select title from card where id = ?", String.class, card)).isEqualTo("A wins");
    }

    @Test
    void concurrentListRenameIs409WithTheCurrentListInTheBody() throws Exception {
        String alice = user("alice");
        UUID board = createBoard(alice, createWorkspace(alice, "ws"));
        UUID list = createList(alice, board);

        call(patch("/api/lists/{id}", list), alice, "{\"name\":\"A wins\",\"version\":0}", 200);
        JsonNode problem = call(patch("/api/lists/{id}", list), alice, "{\"name\":\"B loses\",\"version\":0}", 409);

        assertThat(problem.get("code").asText()).isEqualTo("STALE_VERSION");
        JsonNode current = problem.get("current");
        assertThat(current.get("id").asText()).isEqualTo(list.toString());
        assertThat(current.get("name").asText()).isEqualTo("A wins");
        assertThat(current.get("version").asLong()).isEqualTo(1);
        assertThat(current.get("boardId").asText()).isEqualTo(board.toString());
    }

    @Test
    void concurrentBoardRenameIs409WithTheCurrentBoardInTheBody() throws Exception {
        String alice = user("alice");
        UUID ws = createWorkspace(alice, "ws");
        UUID board = createBoard(alice, ws);

        call(patch("/api/boards/{id}", board), alice, "{\"name\":\"A wins\",\"version\":0}", 200);
        JsonNode problem = call(patch("/api/boards/{id}", board), alice, "{\"name\":\"B loses\",\"version\":0}", 409);

        assertThat(problem.get("code").asText()).isEqualTo("STALE_VERSION");
        JsonNode current = problem.get("current");
        assertThat(current.get("id").asText()).isEqualTo(board.toString());
        assertThat(current.get("name").asText()).isEqualTo("A wins");
        assertThat(current.get("version").asLong()).isEqualTo(1);
        assertThat(current.get("workspaceId").asText()).isEqualTo(ws.toString());
    }

    @Test
    void movingAfterAConcurrentEditIsNeverA409AndKeepsTheEdit() throws Exception {
        String alice = user("alice");
        UUID board = createBoard(alice, createWorkspace(alice, "ws"));
        UUID listA = createList(alice, board);
        UUID listB = createList(alice, board);
        UUID card = createCard(alice, listA);

        call(patch("/api/cards/{id}", card), alice, "{\"title\":\"edited\",\"version\":0}", 200);
        JsonNode moved = call(post("/api/cards/{id}/move", card), alice, "{\"listId\":\"" + listB + "\"}", 200);

        assertThat(moved.get("listId").asText()).isEqualTo(listB.toString());
        assertThat(moved.get("title").asText()).isEqualTo("edited");
        assertThat(moved.get("version").asLong()).isEqualTo(2);
        // un editor que aun tenia la version previa al movimiento recibe 409 con la posicion ganadora
        JsonNode stale = call(patch("/api/cards/{id}", card), alice, "{\"title\":\"late\",\"version\":1}", 409);
        assertThat(stale.at("/current/listId").asText()).isEqualTo(listB.toString());
    }

    @Test
    void concurrentMovesOfTheSameCardAndListAllSucceedLastWriteWins() throws Exception {
        String alice = user("alice");
        UUID board = createBoard(alice, createWorkspace(alice, "ws"));
        UUID listA = createList(alice, board);
        UUID listB = createList(alice, board);
        UUID card = createCard(alice, listA);
        UUID otherCard = createCard(alice, listB);
        int n = 8;

        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Integer>> cardMoves = new ArrayList<>();
            List<Callable<Integer>> listMoves = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UUID target = i % 2 == 0 ? listA : listB;
                cardMoves.add(() -> mvc.perform(post("/api/cards/{id}/move", card).with(as(alice))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"listId\":\"" + target + "\"}"))
                        .andReturn().getResponse().getStatus());
                String body = i % 2 == 0 ? "{\"nextListId\":\"" + listA + "\"}" : "{\"prevListId\":\"" + listA + "\"}";
                listMoves.add(() -> mvc.perform(post("/api/lists/{id}/move", listB).with(as(alice))
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus());
            }
            List<Callable<Integer>> all = new ArrayList<>(cardMoves);
            all.addAll(listMoves);
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(all)) {
                statuses.add(f.get());
            }

            assertThat(statuses).as("ningun movimiento concurrente devuelve 409").containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
        // cada movimiento bumpeo la version exactamente una vez (UPDATE directo serializado por el lock de fila)
        assertThat(jdbc.queryForObject("select version from card where id = ?", Long.class, card)).isEqualTo(n);
        assertThat(jdbc.queryForObject("select version from board_list where id = ?", Long.class, listB))
                .isEqualTo((long) n);
        assertThat(jdbc.queryForObject("select version from card where id = ?", Long.class, otherCard)).isZero();
    }
}
