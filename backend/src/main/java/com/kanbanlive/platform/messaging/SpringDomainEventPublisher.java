package com.kanbanlive.platform.messaging;

import com.kanbanlive.sharedkernel.application.DomainEventPublisher;
import com.kanbanlive.sharedkernel.domain.DomainEvent;
import java.util.Collection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publica cada evento de dominio en el bus de Spring. Los listeners deben ser
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: asi un rollback no emite nada.
 */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Collection<? extends DomainEvent> events) {
        events.forEach(publisher::publishEvent);
    }
}
