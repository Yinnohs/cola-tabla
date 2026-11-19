package com.kanbanlive.platform.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kanbanlive.platform.security.Perms;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** B4.5: SUBSCRIBE/SEND con un token ya vencido se rechazan aunque el cierre de sesion aun no haya ocurrido. */
class StompExpiredTokenFrameTest {

    final Perms perms = mock(Perms.class);
    final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(mock(JwtDecoder.class), perms, true,
            "dev", new SocketTokenLifecycle() {
                @Override
                public void connected(String sessionId, String userId, Instant tokenExpiry) {
                }

                @Override
                public void disconnected(String sessionId) {
                }
            });
    final MessageChannel channel = mock(MessageChannel.class);

    Message<?> frame(StompCommand command, String destination, Instant expiresAt) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("alice")
                .issuedAt(expiresAt.minusSeconds(60)).expiresAt(expiresAt).build();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionId("s1");
        accessor.setUser(new JwtAuthenticationToken(jwt, Collections.emptyList(), "alice"));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribeWithAnExpiredTokenIsRejected() {
        UUID board = UUID.randomUUID();
        when(perms.canViewAs("alice", board)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/topic/boards." + board, Instant.now().minusSeconds(1)), channel))
                .isInstanceOf(MessagingException.class).hasMessageContaining("Token expired");
    }

    @Test
    void sendWithAnExpiredTokenIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, "/app/anything", Instant.now().minusSeconds(1)), channel))
                .isInstanceOf(MessagingException.class).hasMessageContaining("Token expired");
    }

    @Test
    void subscribeWithAValidTokenStillWorks() {
        UUID board = UUID.randomUUID();
        when(perms.canViewAs("alice", board)).thenReturn(true);

        Message<?> message = frame(StompCommand.SUBSCRIBE, "/topic/boards." + board, Instant.now().plusSeconds(60));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
