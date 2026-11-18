package com.kanbanlive.cards.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record CardSummary(UUID id, UUID listId, String title, String description, String position, long version,
        Instant createdAt) {
}
