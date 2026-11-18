package com.kanbanlive.platform.web;

import com.kanbanlive.sharedkernel.application.ClientContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Lee la cabecera opcional {@code X-Client-Id} de la peticion en curso. Valor invalido => se ignora. */
@Component
class RequestClientContext implements ClientContext {

    static final String HEADER = "X-Client-Id";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Override
    public Optional<String> clientId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return Optional.empty();
        }
        HttpServletRequest request = attrs.getRequest();
        String value = request.getHeader(HEADER);
        return value != null && VALID.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
