package com.kanbanlive.sharedkernel.application;

/** Peticion de pagina neutra (0-based), independiente de Spring Data. */
public record PageQuery(int page, int size) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
    }

    public long offset() {
        return (long) page * size;
    }
}
