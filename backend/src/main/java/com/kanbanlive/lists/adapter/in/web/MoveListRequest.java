package com.kanbanlive.lists.adapter.in.web;

import java.util.UUID;

/** Vecinas destino (ambas opcionales: null = extremo del tablero). */
record MoveListRequest(UUID prevListId, UUID nextListId) {
}
