package com.kanbanlive.sharedkernel.application;

import com.kanbanlive.sharedkernel.domain.DomainEvent;
import java.util.Collection;

/** Puerto de salida; lo implementa platform.messaging. */
public interface DomainEventPublisher {

    void publish(Collection<? extends DomainEvent> events);
}
