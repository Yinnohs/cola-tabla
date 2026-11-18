package com.kanbanlive.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.AggregateRoot;
import com.kanbanlive.sharedkernel.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    record Happened(UUID eventId, Instant occurredAt, String aggregateId) implements DomainEvent {
    }

    static class Dummy extends AggregateRoot<String> {
        Dummy(String id, long version) {
            super(id, version);
        }

        void doIt() {
            registerEvent(new Happened(UUID.randomUUID(), Instant.EPOCH, id()));
        }

        void registerNull() {
            registerEvent(null);
        }
    }

    @Test
    void exposesIdAndVersion() {
        Dummy d = new Dummy("a", 3);
        assertThat(d.id()).isEqualTo("a");
        assertThat(d.version()).isEqualTo(3);
    }

    @Test
    void pullEventsReturnsPendingAndClears() {
        Dummy d = new Dummy("a", 0);
        d.doIt();
        d.doIt();
        assertThat(d.pullEvents()).hasSize(2);
        assertThat(d.pullEvents()).isEmpty();
    }

    @Test
    void pulledListIsImmutableSnapshot() {
        Dummy d = new Dummy("a", 0);
        d.doIt();
        var events = d.pullEvents();
        assertThatThrownBy(() -> events.add(events.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullEvent() {
        assertThatThrownBy(() -> new Dummy("a", 0).registerNull()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalityIsByIdAndType() {
        assertThat(new Dummy("a", 0)).isEqualTo(new Dummy("a", 5)).hasSameHashCodeAs(new Dummy("a", 5));
        assertThat(new Dummy("a", 0)).isNotEqualTo(new Dummy("b", 0));
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new Dummy(null, 0)).isInstanceOf(NullPointerException.class);
    }
}
