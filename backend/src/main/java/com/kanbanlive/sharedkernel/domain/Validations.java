package com.kanbanlive.sharedkernel.domain;

/** Guardas de invariantes reutilizables por los agregados. */
public final class Validations {

    private Validations() {
    }

    /** Texto obligatorio: no nulo, no en blanco y de como mucho {@code max} caracteres. Devuelve el valor sin espacios laterales. */
    public static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new ValidationException(field + " must be at most " + max + " characters");
        }
        return trimmed;
    }
}
