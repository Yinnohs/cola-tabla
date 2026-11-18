package com.kanbanlive.sharedkernel.application;

import java.util.Optional;

/**
 * Puerto de salida: identificador de instancia de cliente (pestana/app) que origina la peticion actual, enviado en
 * la cabecera {@code X-Client-Id}. Se propaga en el envelope de los eventos para que ese cliente ignore su eco.
 * Lo implementa platform.web.
 */
public interface ClientContext {

    Optional<String> clientId();
}
