package com.kanbanlive.collaboration.adapter.in.event;

import com.kanbanlive.collaboration.application.port.in.BroadcastBoardEventUseCase;
import com.kanbanlive.collaboration.domain.event.BoardEventRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Solo tras el commit (regla no negociable): nunca se difunde un estado que luego se deshace. */
@Component
class BoardEventRecordedListener {

    private static final Logger LOG = LoggerFactory.getLogger(BoardEventRecordedListener.class);

    private final BroadcastBoardEventUseCase broadcast;

    BoardEventRecordedListener(BroadcastBoardEventUseCase broadcast) {
        this.broadcast = broadcast;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BoardEventRecorded event) {
        try {
            broadcast.execute(event.envelope());
        } catch (RuntimeException e) {
            // El cambio ya esta confirmado en BD; un fallo de difusion no debe convertir la peticion en error.
            // Los clientes lo detectaran por el hueco de seq y resincronizaran.
            LOG.warn("Failed to broadcast board event {} seq {}", event.envelope().boardId(),
                    event.envelope().seq(), e);
        }
    }
}
