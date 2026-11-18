package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.Map;
import java.util.UUID;

/** Eventos de tablero que originan los casos de uso de tarjetas (tipos card.*). Ver docs/contracts/board-events.md. */
final class CardEvents {

    private final ListDirectory lists;
    private final BoardChangePublisher publisher;

    CardEvents(ListDirectory lists, BoardChangePublisher publisher) {
        this.lists = lists;
        this.publisher = publisher;
    }

    void created(CardSummary card) {
        publisher.publish(boardOf(card.listId()), "card.created", Map.of("card", card));
    }

    void updated(CardSummary card) {
        publisher.publish(boardOf(card.listId()), "card.updated", Map.of("card", card));
    }

    void moved(CardSummary card, UUID fromListId) {
        UUID fromBoard = boardOf(fromListId);
        UUID toBoard = boardOf(card.listId());
        if (fromBoard.equals(toBoard)) {
            publisher.publish(toBoard, "card.moved", Map.of("card", card, "fromListId", fromListId));
        } else {
            // Entre tableros: para cada tablero es una baja y un alta.
            publisher.publish(fromBoard, "card.deleted", Map.of("cardId", card.id(), "listId", fromListId));
            publisher.publish(toBoard, "card.created", Map.of("card", card));
        }
    }

    void deleted(UUID cardId, UUID listId) {
        publisher.publish(boardOf(listId), "card.deleted", Map.of("cardId", cardId, "listId", listId));
    }

    private UUID boardOf(UUID listId) {
        return lists.boardIdOf(listId).orElseThrow(() -> new NotFoundException("list " + listId + " not found"));
    }
}
