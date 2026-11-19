package com.kanbanlive.platform.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * B4.5 (ADR-0006): el servidor cierra la sesion STOMP cuando vence el {@code exp} del JWT con el que se conecto.
 * Registra cada sesion con su caducidad y programa (scheduler propio, independiente de @EnableScheduling) el envio
 * de un frame STOMP ERROR; el protocolo cierra el socket tras un ERROR. El cliente renueva el token ~60 s antes y
 * reconecta, de modo que en condiciones normales este cierre nunca se dispara. Sin caducidad (modo local sin
 * seguridad) no se registra nada.
 */
class ExpiringSocketTokenLifecycle implements SocketTokenLifecycle, DisposableBean {

    static final String EXPIRED_MESSAGE = "Token expired";
    private static final Logger LOG = LoggerFactory.getLogger(ExpiringSocketTokenLifecycle.class);

    private final MessageChannel clientOutboundChannel;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "ws-token-expiry");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> sessions = new ConcurrentHashMap<>();

    ExpiringSocketTokenLifecycle(MessageChannel clientOutboundChannel, Clock clock) {
        this.clientOutboundChannel = clientOutboundChannel;
        this.clock = clock;
    }

    @Override
    public void connected(String sessionId, String userId, Instant tokenExpiry) {
        if (tokenExpiry == null || sessionId == null) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(clock.instant(), tokenExpiry).toMillis());
        sessions.put(sessionId, scheduler.schedule(() -> expire(sessionId, userId), delayMillis, TimeUnit.MILLISECONDS));
    }

    @Override
    public void disconnected(String sessionId) {
        ScheduledFuture<?> pending = sessions.remove(sessionId);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    /** Numero de sesiones con caducidad pendiente (diagnostico/tests). */
    int trackedSessions() {
        return sessions.size();
    }

    private void expire(String sessionId, String userId) {
        if (sessions.remove(sessionId) == null) {
            return;
        }
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            accessor.setSessionId(sessionId);
            accessor.setMessage(EXPIRED_MESSAGE);
            accessor.setLeaveMutable(true);
            Message<byte[]> error = MessageBuilder.createMessage(EXPIRED_MESSAGE.getBytes(StandardCharsets.UTF_8),
                    accessor.getMessageHeaders());
            clientOutboundChannel.send(error);
            LOG.info("Closed WebSocket session {} of user {}: token expired", sessionId, userId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to close expired WebSocket session {}", sessionId, e);
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}
