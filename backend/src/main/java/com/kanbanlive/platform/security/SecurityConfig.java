package com.kanbanlive.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Fase 0: resource server JWT minimo. La autorizacion por rol/membresia llega en la Fase 2. */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "kanban.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Documentacion OpenAPI: solo existe si springdoc esta habilitado (perfiles local/test).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * Solo desarrollo local (`kanban.security.enabled=false`): el frontend aun no obtiene tokens
     * (Keycloak llega en la Fase 2), asi que se permite todo sin autenticar.
     */
    @Bean
    @ConditionalOnProperty(name = "kanban.security.enabled", havingValue = "false")
    SecurityFilterChain openSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
