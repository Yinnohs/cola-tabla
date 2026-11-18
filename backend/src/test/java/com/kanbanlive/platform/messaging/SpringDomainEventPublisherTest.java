package com.kanbanlive.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.sharedkernel.domain.DomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringDomainEventPublisherTest {

    record Fake(UUID eventId, Instant occurredAt, String aggregateId) implements DomainEvent {
    }

    @Test
    void publishesEachEventInOrderToTheSpringBus() {
        List<Object> published = new ArrayList<>();
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(published::add);
        Fake a = new Fake(UUID.randomUUID(), Instant.now(), "1");
        Fake b = new Fake(UUID.randomUUID(), Instant.now(), "2");

        publisher.publish(List.of(a, b));

        assertThat(published).containsExactly(a, b);
    }

    @Test
    void emptyCollectionPublishesNothing() {
        List<Object> published = new ArrayList<>();
        new SpringDomainEventPublisher(published::add).publish(List.of());
        assertThat(published).isEmpty();
    }
}
