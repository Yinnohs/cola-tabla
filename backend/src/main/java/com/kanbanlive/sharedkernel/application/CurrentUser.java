package com.kanbanlive.sharedkernel.application;

/** Puerto de salida: identidad del usuario autenticado en la peticion actual (lo implementa platform.security). */
public interface CurrentUser {

    /** Identificador estable del usuario (claim sub del JWT). */
    String id();

    /**
     * true solo en desarrollo local con la seguridad desactivada: el usuario ve todos los recursos
     * (los listados no se filtran por membresia).
     */
    default boolean unrestricted() {
        return false;
    }
}
