package com.kanbanlive.cards.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Lista destino y vecinas en ella (prev/next opcionales: null = extremo de la lista). */
record MoveCardRequest(@NotNull UUID listId, UUID prevCardId, UUID nextCardId) {
}
