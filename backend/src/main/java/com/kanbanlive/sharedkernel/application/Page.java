package com.kanbanlive.sharedkernel.application;

import java.util.List;
import java.util.function.Function;

/** Pagina de resultados neutra. */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        content = List.copyOf(content);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        return new Page<>(content.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
