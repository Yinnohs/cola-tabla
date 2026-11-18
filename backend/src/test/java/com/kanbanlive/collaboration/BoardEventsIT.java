package com.kanbanlive.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanbanlive.testsupport.PostgresIT;
import com.kanbanlive.testsupport.StompTestClient;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * B3.8 - REST -> evento -> STOMP de extremo a extremo con JWT firmados: un miembro suscrito recibe un envelope por
 * mutacion con seq estrictamente creciente, el clientId viaja de ida y vuelta, y quien no es miembro no recibe nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(BoardEventsIT.TestKeys.class)
class BoardEventsIT extends PostgresIT {

    static final KeyPair KEYS = generate();

    static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration
    static class TestKeys {
        @Bean
        @Primary
        JwtDecoder boardEventsJwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        }
    }

    @LocalServerPort
    int port;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);

    String user(String n) {
        return n + "-" + suffix;
    }

    String tokenFor(String sub) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(sub)
                .expirationTime(new Date(System.currentTimeMillis() + 300_000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    RequestPostProcessor as(String sub) {
        return jwt().jwt(j -> j.subject(sub));
    }

    JsonNode call(MockHttpServletRequestBuilder request, String sub, String clientId, String body, int expected)
            throws Exception {
        request.with(as(sub));
        if (clientId != null) {
            request.header("X-Client-Id", clientId);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String json = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString();
        return json.isBlank() ? null : mapper.readTree(json);
    }

    UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    List<JsonNode> receive(StompTestClient c, int count) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String m = c.nextMessage(5000);
            assertThat(m).as("message %d of %d", i + 1, count).isNotNull();
            out.add(mapper.readTree(m));
        }
        return out;
    }

    @Test
    void memberReceivesOneEnvelopePerMutationWithIncreasingSeqAndClientIdRoundTrip() throws Exception {
        String alice = user("alice");
        String bob = user("bob");
        UUID wsA = id(call(post("/api/workspaces"), alice, null, "{\"name\":\"A\"}", 201));
        UUID boardA = id(call(post("/api/workspaces/{id}/boards", wsA), alice, null, "{\"name\":\"B\"}", 201));
        UUID wsB = id(call(post("/api/workspaces"), bob, null, "{\"name\":\"B\"}", 201));
        UUID boardB = id(call(post("/api/workspaces/{id}/boards", wsB), bob, null, "{\"name\":\"B\"}", 201));

        try (StompTestClient aliceWs = StompTestClient.connect(port, tokenFor(alice));
                StompTestClient bobWs = StompTestClient.connect(port, tokenFor(bob))) {
            assertThat(aliceWs.awaitConnected()).isTrue();
            assertThat(bobWs.awaitConnected()).isTrue();
            aliceWs.subscribe("/topic/boards." + boardA);
            bobWs.subscribe("/topic/boards." + boardB);
            Thread.sleep(500);

            JsonNode list1 = call(post("/api/boards/{id}/lists", boardA), alice, "tab-1", "{\"name\":\"L1\"}", 201);
            JsonNode list2 = call(post("/api/boards/{id}/lists", boardA), alice, "tab-1", "{\"name\":\"L2\"}", 201);
            JsonNode card = call(post("/api/lists/{id}/cards", id(list1)), alice, "tab-1", "{\"title\":\"C\"}", 201);
            call(patch("/api/cards/{id}", id(card)), alice, "tab-2", "{\"title\":\"C2\",\"version\":0}", 200);
            call(post("/api/cards/{id}/move", id(card)), alice, "tab-1", "{\"listId\":\"" + id(list2) + "\"}", 200);
            call(patch("/api/lists/{id}", id(list1)), alice, "tab-1", "{\"name\":\"L1b\",\"version\":0}", 200);
            call(post("/api/lists/{id}/move", id(list2)), alice, "bad value!",
                    "{\"nextListId\":\"" + id(list1) + "\"}", 200);
            call(patch("/api/boards/{id}", boardA), alice, "tab-1", "{\"name\":\"Renamed\",\"version\":0}", 200);
            call(delete("/api/cards/{id}", id(card)), alice, "tab-1", null, 204);

            List<JsonNode> events = receive(aliceWs, 9);

            assertThat(events.stream().map(e -> e.get("type").asText())).containsExactly(
                    "list.created", "list.created", "card.created", "card.updated", "card.moved", "list.updated",
                    "list.moved", "board.updated", "card.deleted");
            assertThat(events.stream().map(e -> e.get("seq").asLong())).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L,
                    8L, 9L);
            assertThat(events).allSatisfy(e -> {
                assertThat(e.get("boardId").asText()).isEqualTo(boardA.toString());
                assertThat(e.get("schemaVersion").asInt()).isEqualTo(1);
                assertThat(e.get("occurredAt").asText()).isNotBlank();
                assertThat(e.get("payload").isObject()).isTrue();
            });
            // clientId de ida y vuelta (tab-2 en el update; el valor invalido se descarta => null).
            assertThat(events.get(0).get("clientId").asText()).isEqualTo("tab-1");
            assertThat(events.get(3).get("clientId").asText()).isEqualTo("tab-2");
            assertThat(events.get(6).get("clientId").isNull()).isTrue();
            assertThat(events.get(4).at("/payload/card/listId").asText()).isEqualTo(id(list2).toString());
            assertThat(events.get(4).at("/payload/fromListId").asText()).isEqualTo(id(list1).toString());
            assertThat(events.get(8).at("/payload/cardId").asText()).isEqualTo(id(card).toString());

            // El log persistido coincide con lo difundido.
            assertThat(jdbc.queryForList("select seq from board_event where board_id = ? order by seq", Long.class,
                    boardA)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

            // Bob (otro workspace) no recibe nada del tablero A, ni siquiera estando suscrito a su propio tablero.
            assertThat(bobWs.nextMessage(1000)).isNull();
            bobWs.subscribe("/topic/boards." + boardA);
            assertThat(bobWs.nextError(3000)).isNotNull();
        }
    }

    @Test
    void failedRequestEmitsNothingAndConsumesNoSeq() throws Exception {
        String alice = user("alice");
        UUID ws = id(call(post("/api/workspaces"), alice, null, "{\"name\":\"A\"}", 201));
        UUID board = id(call(post("/api/workspaces/{id}/boards", ws), alice, null, "{\"name\":\"B\"}", 201));
        try (StompTestClient c = StompTestClient.connect(port, tokenFor(alice))) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + board);
            Thread.sleep(500);

            // Version obsoleta => 409, sin evento.
            call(patch("/api/boards/{id}", board), alice, "tab-1", "{\"name\":\"X\",\"version\":42}", 409);
            assertThat(c.nextMessage(1000)).isNull();

            call(patch("/api/boards/{id}", board), alice, "tab-1", "{\"name\":\"X\",\"version\":0}", 200);
            JsonNode event = receive(c, 1).get(0);
            assertThat(event.get("seq").asLong()).isEqualTo(1L);
        }
    }
}
