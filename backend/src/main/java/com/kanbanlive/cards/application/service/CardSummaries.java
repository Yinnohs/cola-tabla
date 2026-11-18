package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.domain.model.Card;

final class CardSummaries {

    private CardSummaries() {
    }

    static CardSummary of(Card c) {
        return new CardSummary(c.id(), c.listId(), c.title(), c.description(), c.position().value(), c.version(),
                c.createdAt());
    }
}
