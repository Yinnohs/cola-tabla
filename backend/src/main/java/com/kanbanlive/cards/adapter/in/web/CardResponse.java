package com.kanbanlive.cards.adapter.in.web;

import com.kanbanlive.cards.application.port.in.CardSummary;
import java.time.Instant;
import java.util.UUID;

record CardResponse(UUID id, UUID listId, String title, String description, String position, long version,
        Instant createdAt) {

    static CardResponse from(CardSummary s) {
        return new CardResponse(s.id(), s.listId(), s.title(), s.description(), s.position(), s.version(),
                s.createdAt());
    }
}
