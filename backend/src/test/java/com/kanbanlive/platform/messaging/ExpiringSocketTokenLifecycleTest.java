package com.kanbanlive.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

/** B4.5 sin infraestructura: registro de sesiones, ERROR al vencer, cancelacion al desconectar. */
class ExpiringSocketTokenLifecycleTest {

    final List<Message<?>> sent = new CopyOnWriteArrayList<>();
    CountDownLatch firstSend = new CountDownLatch(1);
    final MessageChannel channel = (message, timeout) -> {
        sent.add(message);
        firstSend.countDown();
        return true;
    };
    final ExpiringSocketTokenLifecycle lifecycle = new ExpiringSocketTokenLifecycle(channel, Clock.systemUTC());

    @AfterEach
    void stop() {
        lifecycle.destroy();
    }

    @Test
    void sendsAStompErrorToTheSessionWhenTheTokenExpires() throws Exception {
        lifecycle.connected("s1", "alice", Instant.now().plusMillis(150));
        assertThat(lifecycle.trackedSessions()).isEqualTo(1);

        assertThat(firstSend.await(3, TimeUnit.SECONDS)).isTrue();

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sent.get(0));
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getSessionId()).isEqualTo("s1");
        assertThat(accessor.getMessage()).isEqualTo("Token expired");
        assertThat(lifecycle.trackedSessions()).isZero();
    }

    @Test
    void anAlreadyExpiredTokenIsClosedImmediately() throws Exception {
        lifecycle.connected("s1", "alice", Instant.now().minusSeconds(5));

        assertThat(firstSend.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void sessionsWithoutExpiryAreNeverTracked() throws Exception {
        lifecycle.connected("local", "dev-user", null);

        assertThat(lifecycle.trackedSessions()).isZero();
        assertThat(firstSend.await(300, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void disconnectingBeforeExpiryCancelsTheClose() throws Exception {
        lifecycle.connected("s1", "alice", Instant.now().plusMillis(200));
        lifecycle.disconnected("s1");

        assertThat(lifecycle.trackedSessions()).isZero();
        assertThat(firstSend.await(600, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    void onlyTheExpiredSessionIsClosed() throws Exception {
        lifecycle.connected("short", "alice", Instant.now().plusMillis(100));
        lifecycle.connected("long", "bob", Instant.now().plusSeconds(60));

        assertThat(firstSend.await(3, TimeUnit.SECONDS)).isTrue();

        assertThat(sent).hasSize(1);
        assertThat(StompHeaderAccessor.wrap(sent.get(0)).getSessionId()).isEqualTo("short");
        assertThat(lifecycle.trackedSessions()).isEqualTo(1);
    }
}
