package com.kanbanlive.cards.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Reemplaza titulo y descripcion (description null la borra); version obligatoria (concurrencia optimista). */
record UpdateCardRequest(@NotBlank @Size(max = 255) String title, @Size(max = 10000) String description,
        @NotNull Long version) {
}
