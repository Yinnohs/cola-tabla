package com.kanbanlive.testsupport;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.AbstractMessageConverter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Cliente STOMP real (WebSocket nativo) para tests: conecta con un token y registra mensajes y errores. */
public final class StompTestClient implements AutoCloseable {

    private static final long WAIT_SECONDS = 5;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    /** Cuerpos de los mensajes recibidos en las suscripciones. */
    public final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    /** Tramas ERROR o errores de transporte (cierre inesperado). */
    public final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private volatile StompSession session;

    /** Entrega el cuerpo tal cual como texto UTF-8, sea cual sea el content-type (text/plain o application/json). */
    private static final class RawTextConverter extends AbstractMessageConverter {

        RawTextConverter() {
            super(MimeTypeUtils.ALL);
        }

        @Override
        protected boolean supports(Class<?> clazz) {
            return String.class == clazz;
        }

        @Override
        protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object hint) {
            Object payload = message.getPayload();
            return payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : payload.toString();
        }

        @Override
        protected Object convertToInternal(Object payload, org.springframework.messaging.MessageHeaders headers,
                Object hint) {
            return ((String) payload).getBytes(StandardCharsets.UTF_8);
        }
    }

    private StompTestClient() {
        client.setMessageConverter(new RawTextConverter());
    }

    /** @param bearerToken null para no enviar cabecera Authorization */
    public static StompTestClient connect(int port, String bearerToken) {
        StompTestClient c = new StompTestClient();
        StompHeaders headers = new StompHeaders();
        if (bearerToken != null) {
            headers.add("Authorization", "Bearer " + bearerToken);
        }
        c.client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession s, StompHeaders h) {
                        c.session = s;
                        c.connected.complete(null);
                    }

                    @Override
                    public void handleFrame(StompHeaders h, Object payload) {
                        c.errors.add("ERROR frame: " + h.getFirst("message"));
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable ex) {
                        c.errors.add("transport: " + ex);
                    }

                    @Override
                    public void handleException(StompSession s, StompCommand cmd, StompHeaders h, byte[] payload,
                            Throwable ex) {
                        c.errors.add("exception: " + ex);
                    }
                });
        return c;
    }

    /** true si el servidor acepto el CONNECT (CONNECTED recibido). */
    public boolean awaitConnected() {
        try {
            connected.get(WAIT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void subscribe(String destination) {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((String) payload);
            }
        });
    }

    /** true mientras el socket STOMP sigue abierto (false tras un cierre del servidor). */
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    public void send(String destination, String body) {
        session.send(destination, body);
    }

    public String nextMessage(long millis) throws InterruptedException {
        return messages.poll(millis, TimeUnit.MILLISECONDS);
    }

    public String nextError(long millis) throws InterruptedException {
        return errors.poll(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        } catch (RuntimeException alreadyClosed) {
            // El servidor puede haber cerrado el socket justo antes (p. ej. tras un ERROR por acceso denegado).
        }
        client.stop();
    }
}
