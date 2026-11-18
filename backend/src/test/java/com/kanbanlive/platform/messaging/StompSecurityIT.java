package com.kanbanlive.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.Date;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * B3.1 + B3.2 - STOMP real (WebSocketStompClient) con JWT firmados y Postgres: CONNECT exige token valido y
 * SUBSCRIBE a /topic/boards.{id} exige membresia del workspace del tablero. Incluye el test negativo
 * obligatorio: un usuario del workspace B no puede suscribirse a un tablero del workspace A.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(StompSecurityIT.TestKeys.class)
class StompSecurityIT extends PostgresIT {

    static final KeyPair KEYS = generate();
    static final KeyPair OTHER_KEYS = generate();
    static final long WAIT = 1500;

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
        JwtDecoder stompSignedJwtDecoder() {
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
    SimpMessagingTemplate broker;

    final String suffix = UUID.randomUUID().toString().substring(0, 8);

    String user(String n) {
        return n + "-" + suffix;
    }

    static String token(KeyPair signWith, String sub, long expiresInSeconds) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(sub)
                .expirationTime(new Date(System.currentTimeMillis() + expiresInSeconds * 1000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signWith.getPrivate()));
        return jwt.serialize();
    }

    String tokenFor(String sub) throws Exception {
        return token(KEYS, sub, 300);
    }

    RequestPostProcessor as(String sub) {
        return jwt().jwt(j -> j.subject(sub));
    }

    UUID createWorkspace(String sub) throws Exception {
        String json = mvc.perform(post("/api/workspaces").with(as(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"W\"}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return UUID.fromString(mapper.readTree(json).get("id").asText());
    }

    UUID createBoard(String sub, UUID workspace) throws Exception {
        String json = mvc.perform(post("/api/workspaces/{id}/boards", workspace).with(as(sub))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"B\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(json).get("id").asText());
    }

    UUID boardOf(String owner) throws Exception {
        return createBoard(owner, createWorkspace(owner));
    }

    @Test
    void ownerConnectsSubscribesAndReceivesBoardMessages() throws Exception {
        String alice = user("alice");
        UUID board = boardOf(alice);

        try (StompTestClient c = StompTestClient.connect(port, tokenFor(alice))) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + board);
            Thread.sleep(300);
            broker.convertAndSend("/topic/boards." + board, "hello");

            assertThat(c.nextMessage(3000)).isEqualTo("hello");
            assertThat(c.errors).isEmpty();
        }
    }

    @Test
    void userOfWorkspaceBCannotSubscribeToBoardOfWorkspaceA() throws Exception {
        String alice = user("alice");
        String bob = user("bob");
        UUID boardA = boardOf(alice);
        boardOf(bob);

        try (StompTestClient c = StompTestClient.connect(port, tokenFor(bob))) {
            assertThat(c.awaitConnected()).as("bob connects: valid token").isTrue();
            c.subscribe("/topic/boards." + boardA);

            assertThat(c.nextError(3000)).as("server rejects the subscription").isNotNull();
            broker.convertAndSend("/topic/boards." + boardA, "secret of A");
            assertThat(c.nextMessage(WAIT)).as("bob never receives events of A").isNull();
        }
    }

    @Test
    void subscribingToAnUnknownBoardIsDeniedLikeAForeignOne() throws Exception {
        try (StompTestClient c = StompTestClient.connect(port, tokenFor(user("carol")))) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + UUID.randomUUID());

            assertThat(c.nextError(3000)).isNotNull();
        }
    }

    @Test
    void viewerMemberCanSubscribe() throws Exception {
        String alice = user("alice");
        String viewer = user("viewer");
        UUID ws = createWorkspace(alice);
        UUID board = createBoard(alice, ws);
        mvc.perform(post("/api/workspaces/{id}/members", ws).with(as(alice)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewer + "\",\"role\":\"VIEWER\"}")).andExpect(status().isCreated());

        try (StompTestClient c = StompTestClient.connect(port, tokenFor(viewer))) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + board);
            Thread.sleep(300);
            broker.convertAndSend("/topic/boards." + board, "to viewers");

            assertThat(c.nextMessage(3000)).isEqualTo("to viewers");
        }
    }

    @Test
    void connectWithoutTokenIsRejected() {
        try (StompTestClient c = StompTestClient.connect(port, null)) {
            assertThat(c.awaitConnected()).isFalse();
        }
    }

    @Test
    void connectWithForgedTokenIsRejected() throws Exception {
        try (StompTestClient c = StompTestClient.connect(port, token(OTHER_KEYS, "mallory", 300))) {
            assertThat(c.awaitConnected()).isFalse();
        }
    }

    @Test
    void connectWithExpiredTokenIsRejected() throws Exception {
        try (StompTestClient c = StompTestClient.connect(port, token(KEYS, "alice", -300))) {
            assertThat(c.awaitConnected()).isFalse();
        }
    }

    @Test
    void connectWithGarbageTokenIsRejected() {
        try (StompTestClient c = StompTestClient.connect(port, "not-a-jwt")) {
            assertThat(c.awaitConnected()).isFalse();
        }
    }

    @Test
    void otherDestinationsAreDenied() throws Exception {
        String alice = user("alice");
        UUID board = boardOf(alice);
        try (StompTestClient c = StompTestClient.connect(port, tokenFor(alice))) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/anything-else");
            assertThat(c.nextError(3000)).isNotNull();
        }
        // Un cliente no puede inyectar mensajes directamente en un topic (solo /app/**).
        try (StompTestClient owner = StompTestClient.connect(port, tokenFor(alice));
                StompTestClient attacker = StompTestClient.connect(port, tokenFor(alice))) {
            assertThat(owner.awaitConnected()).isTrue();
            assertThat(attacker.awaitConnected()).isTrue();
            owner.subscribe("/topic/boards." + board);
            Thread.sleep(300);
            attacker.send("/topic/boards." + board, "forged");

            assertThat(attacker.nextError(3000)).isNotNull();
            assertThat(owner.nextMessage(WAIT)).isNull();
        }
    }
}
