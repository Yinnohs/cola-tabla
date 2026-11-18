package com.kanbanlive.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.testsupport.PostgresIT;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B2.7 - JWT REALMENTE firmados (RS256) pasan por el filtro de resource server con un decoder que valida firma:
 * sin Keycloak, pero sin saltarse la verificacion como hace el post-processor jwt(). El sub del token es el
 * usuario que se vuelve OWNER al crear un workspace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SignedJwtIT.TestKeys.class)
class SignedJwtIT extends PostgresIT {

    static final KeyPair KEYS = generate();
    static final KeyPair OTHER_KEYS = generate();

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
        JwtDecoder signedJwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        }
    }

    @Autowired
    MockMvc mvc;

    static String token(KeyPair signWith, String sub, long expiresInSeconds) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(sub)
                .expirationTime(new Date(System.currentTimeMillis() + expiresInSeconds * 1000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signWith.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void validSignedTokenWorksAndSubBecomesOwner() throws Exception {
        String bearer = "Bearer " + token(KEYS, "kc-sub-1", 300);
        String json = mvc.perform(post("/api/workspaces").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Signed\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/workspaces/{id}", id).header("Authorization", bearer)).andExpect(status().isOk());
        String other = "Bearer " + token(KEYS, "kc-sub-2", 300);
        mvc.perform(get("/api/workspaces/{id}", id).header("Authorization", other)).andExpect(status().isForbidden());
    }

    @Test
    void forgedOrExpiredTokenIs401() throws Exception {
        mvc.perform(get("/api/workspaces").header("Authorization", "Bearer " + token(OTHER_KEYS, "x", 300)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces").header("Authorization", "Bearer " + token(KEYS, "x", -300)))
                .andExpect(status().isUnauthorized());
    }
}
