package com.kanbanlive.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import java.time.Duration;
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

/**
 * B4.5 / ADR-0006 - STOMP real con JWT firmados de vida corta: el servidor envia un ERROR y cierra el socket al
 * vencer el {@code exp}; antes de eso la sesion funciona con normalidad; quien renueva el token y reconecta sigue
 * recibiendo eventos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TokenExpiryIT.TestKeys.class)
class TokenExpiryIT extends PostgresIT {

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
        JwtDecoder tokenExpiryJwtDecoder() {
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

    static String token(String sub, long expiresInMillis) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(sub)
                .expirationTime(new Date(System.currentTimeMillis() + expiresInMillis)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    UUID boardOf(String owner) throws Exception {
        String ws = mvc.perform(post("/api/workspaces").with(jwt().jwt(j -> j.subject(owner)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"W\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID workspace = UUID.fromString(mapper.readTree(ws).get("id").asText());
        String board = mvc.perform(post("/api/workspaces/{id}/boards", workspace).with(jwt().jwt(j -> j.subject(owner)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"B\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(board).get("id").asText());
    }

    @Test
    void serverClosesTheSessionWithAnErrorFrameWhenTheTokenExpires() throws Exception {
        String alice = "alice-" + suffix;
        UUID board = boardOf(alice);

        try (StompTestClient c = StompTestClient.connect(port, token(alice, 2500))) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + board);
            Thread.sleep(300);
            broker.convertAndSend("/topic/boards." + board, "before-expiry");
            assertThat(c.nextMessage(1500)).isEqualTo("before-expiry");
            assertThat(c.isConnected()).isTrue();

            String error = c.nextError(8000);
            assertThat(error).as("ERROR frame sent by the server at exp").contains("Token expired");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(c.isConnected()).isFalse());

            // y ya no llegan mensajes por esa sesion
            broker.convertAndSend("/topic/boards." + board, "after-expiry");
            assertThat(c.nextMessage(800)).isNull();
        }
    }

    @Test
    void aSessionWithAFreshTokenIsNotAffectedByAnotherSessionsExpiry() throws Exception {
        String alice = "alice2-" + suffix;
        UUID board = boardOf(alice);

        try (StompTestClient shortLived = StompTestClient.connect(port, token(alice, 1500));
                StompTestClient renewed = StompTestClient.connect(port, token(alice, 300_000))) {
            assertThat(shortLived.awaitConnected()).isTrue();
            assertThat(renewed.awaitConnected()).isTrue();
            renewed.subscribe("/topic/boards." + board);
            Thread.sleep(300);

            assertThat(shortLived.nextError(8000)).contains("Token expired");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(shortLived.isConnected()).isFalse());

            // el cliente que renovo antes (nueva conexion con token nuevo) sigue vivo y recibiendo
            assertThat(renewed.isConnected()).isTrue();
            broker.convertAndSend("/topic/boards." + board, "still-here");
            assertThat(renewed.nextMessage(2000)).isEqualTo("still-here");
        }
    }
}
