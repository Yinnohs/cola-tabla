package com.kanbanlive.platform.messaging;

import com.kanbanlive.platform.security.Perms;
import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Canal de entrada STOMP: autentica en CONNECT (JWT en la cabecera STOMP {@code Authorization}, no en el upgrade
 * HTTP) y autoriza SUBSCRIBE a {@code /topic/boards.{id}} con el mismo chequeo que {@code @perms.canView}.
 * Todo lo no previsto se deniega (default deny); los clientes solo pueden enviar a {@code /app/**}.
 */
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/boards\\.([0-9a-fA-F-]{36})$");

    private final JwtDecoder jwtDecoder;
    private final Perms perms;
    private final boolean securityEnabled;
    private final String devUserId;
    private final SocketTokenLifecycle lifecycle;

    StompAuthChannelInterceptor(JwtDecoder jwtDecoder, Perms perms, boolean securityEnabled, String devUserId,
            SocketTokenLifecycle lifecycle) {
        this.jwtDecoder = jwtDecoder;
        this.perms = perms;
        this.securityEnabled = securityEnabled;
        this.devUserId = devUserId;
        this.lifecycle = lifecycle;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> connect(accessor);
            case SUBSCRIBE -> subscribe(accessor);
            case SEND -> send(accessor);
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK...: sin chequeo adicional.
            }
        }
        return message;
    }

    private void connect(StompHeaderAccessor accessor) {
        if (!securityEnabled) {
            // Solo local (kanban.security.enabled=false): usuario de desarrollo, sin token.
            accessor.setUser(new UsernamePasswordAuthenticationToken(devUserId, null, Collections.emptyList()));
            lifecycle.connected(accessor.getSessionId(), devUserId, null);
            return;
        }
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7) || header.length() <= 7) {
            throw new MessagingException("Missing bearer token");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(7).trim());
        } catch (JwtException e) {
            throw new MessagingException("Invalid token");
        }
        if (jwt.getSubject() == null) {
            throw new MessagingException("Token without subject");
        }
        accessor.setUser(new JwtAuthenticationToken(jwt, Collections.emptyList(), jwt.getSubject()));
        lifecycle.connected(accessor.getSessionId(), jwt.getSubject(), jwt.getExpiresAt());
    }

    /** Comprobacion barata (B4.5): un token vencido no autoriza mas frames aunque el cierre aun no haya ocurrido. */
    private void requireUnexpired(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof JwtAuthenticationToken auth) {
            Instant expiresAt = auth.getToken().getExpiresAt();
            if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
                throw new MessagingException(ExpiringSocketTokenLifecycle.EXPIRED_MESSAGE);
            }
        }
    }

    private void subscribe(StompHeaderAccessor accessor) {
        String userId = userId(accessor).orElseThrow(() -> new MessagingException("Not authenticated"));
        requireUnexpired(accessor);
        String destination = accessor.getDestination();
        Matcher m = destination == null ? null : BOARD_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessagingException("Destination not allowed");
        }
        UUID boardId;
        try {
            boardId = UUID.fromString(m.group(1));
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Destination not allowed");
        }
        if (!perms.canViewAs(userId, boardId)) {
            // Mismo resultado para "no existe" y "no eres miembro": no revela la existencia del tablero.
            throw new MessagingException("Access denied");
        }
    }

    private void send(StompHeaderAccessor accessor) {
        userId(accessor).orElseThrow(() -> new MessagingException("Not authenticated"));
        requireUnexpired(accessor);
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            throw new MessagingException("Destination not allowed");
        }
    }

    private static Optional<String> userId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        return user == null ? Optional.empty() : Optional.of(user.getName());
    }
}
