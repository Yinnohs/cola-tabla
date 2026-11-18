package com.kanbanlive.platform.messaging;

import java.time.Instant;

/**
 * Punto de extension (B3.9) para la expiracion del JWT con el socket abierto. La decision (cerrar al expirar,
 * pedir re-auth por frame, renovar) se toma en Fase 4 (B4.5). Por defecto no hace nada: el token solo se valida
 * en CONNECT. Un bean propio de este tipo sustituye al por defecto.
 */
public interface SocketTokenLifecycle {

    /** CONNECT autenticado. {@code tokenExpiry} es null si la seguridad esta desactivada (modo local). */
    void connected(String sessionId, String userId, Instant tokenExpiry);

    void disconnected(String sessionId);
}
