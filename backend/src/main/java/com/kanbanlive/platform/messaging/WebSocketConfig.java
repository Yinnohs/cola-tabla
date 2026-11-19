package com.kanbanlive.platform.messaging;

import com.kanbanlive.platform.security.Perms;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** STOMP sobre WebSocket nativo: endpoint /ws, broker simple en /topic, prefijo de aplicacion /app. */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final Perms perms;
    private final SocketTokenLifecycle lifecycle;
    private final boolean securityEnabled;
    private final String devUserId;
    private final String[] allowedOrigins;

    WebSocketConfig(JwtDecoder jwtDecoder, Perms perms, SocketTokenLifecycle lifecycle,
            @Value("${kanban.security.enabled:true}") boolean securityEnabled,
            @Value("${kanban.security.dev-user-id:dev-user}") String devUserId,
            @Value("${kanban.ws.allowed-origin-patterns:*}") String[] allowedOrigins) {
        this.jwtDecoder = jwtDecoder;
        this.perms = perms;
        this.lifecycle = lifecycle;
        this.securityEnabled = securityEnabled;
        this.devUserId = devUserId;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // La autenticacion va en CONNECT (token en cabecera STOMP), no en el handshake HTTP.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                new StompAuthChannelInterceptor(jwtDecoder, perms, securityEnabled, devUserId, lifecycle),
                // Propaga el usuario del mensaje al SecurityContext del hilo que lo procesa (CurrentUser, @perms).
                new SecurityContextChannelInterceptor());
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        lifecycle.disconnected(event.getSessionId());
    }

    /**
     * Cierra la sesion cuando vence el JWT (B4.5, ADR-0006). El canal de salida se inyecta perezoso: lo crea la
     * propia infraestructura STOMP que depende de esta configuracion. Un bean propio de {@link SocketTokenLifecycle}
     * lo sustituye.
     */
    @Bean
    @ConditionalOnMissingBean(SocketTokenLifecycle.class)
    static SocketTokenLifecycle expiringSocketTokenLifecycle(
            @Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        return new ExpiringSocketTokenLifecycle(clientOutboundChannel, Clock.systemUTC());
    }
}
