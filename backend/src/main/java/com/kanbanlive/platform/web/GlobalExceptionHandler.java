package com.kanbanlive.platform.web;

import com.kanbanlive.sharedkernel.domain.BadRequestException;
import com.kanbanlive.sharedkernel.domain.ConflictException;
import com.kanbanlive.sharedkernel.domain.DomainException;
import com.kanbanlive.sharedkernel.domain.ForbiddenException;
import com.kanbanlive.sharedkernel.domain.GoneException;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce errores a RFC 7807 {@link ProblemDetail}. Todas las respuestas llevan la propiedad estable
 * {@code code} (contrato con el frontend): {@code NOT_FOUND}, {@code STALE_VERSION} (con {@code current}), {@code CONFLICT}, {@code BAD_REQUEST}, {@code GONE},
 * {@code FORBIDDEN}, {@code VALIDATION}, {@code INTERNAL_ERROR}. Los errores propios de Spring MVC
 * (JSON malformado, 405, ...) los resuelve la clase base como ProblemDetail y se les anade {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";
    public static final String CURRENT = "current";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex);
    }

    /** Incluye StaleVersionException (code STALE_VERSION). */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, ex);
        if (ex instanceof StaleVersionException stale && stale.current() != null) {
            // ADR-0005: el estado actual viaja en el 409 para que el cliente compare y reintente sin otro GET.
            pd.setProperty(CURRENT, stale.current());
        }
        return pd;
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex);
    }

    /** 410: p. ej. EVENTS_GONE, el cliente debe recargar el tablero completo. */
    @ExceptionHandler(GoneException.class)
    ProblemDetail gone(GoneException ex) {
        return problem(HttpStatus.GONE, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validation(ValidationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    /** Cualquier DomainException futura sin mapeo especifico: se trata como invariante violada. */
    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = base(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION", "Validation failed");
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", String.valueOf(v.getMessage())))
                .toList();
        pd.setProperty(ERRORS, errors);
        return pd;
    }

    /** @PreAuthorize denegado (bean perms): 403 uniforme, sin distinguir recurso inexistente de ajeno. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException ex) {
        return base(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = base(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION", "Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        pd.setProperty(ERRORS, errors);
        return ResponseEntity.unprocessableEntity().body(pd);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd && (pd.getProperties() == null || !pd.getProperties().containsKey(CODE))) {
            pd.setProperty(CODE, statusCode.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR");
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, DomainException ex) {
        return base(status, ex.code(), ex.getMessage());
    }

    private static ProblemDetail base(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:kanbanlive:problem:" + code.toLowerCase().replace('_', '-')));
        pd.setProperty(CODE, code);
        return pd;
    }
}
