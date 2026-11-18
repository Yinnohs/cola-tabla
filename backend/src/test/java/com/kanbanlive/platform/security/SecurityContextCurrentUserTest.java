package com.kanbanlive.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityContextCurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsSubFromJwt() {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "kc-user-1"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        assertThat(new SecurityContextCurrentUser(true, "dev-user").id()).isEqualTo("kc-user-1");
    }

    @Test
    void securityEnabledWithoutAuthenticationFails() {
        assertThatThrownBy(() -> new SecurityContextCurrentUser(true, "dev-user").id())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void securityDisabledFallsBackToDevUser() {
        assertThat(new SecurityContextCurrentUser(false, "dev-user").id()).isEqualTo("dev-user");
    }
}
