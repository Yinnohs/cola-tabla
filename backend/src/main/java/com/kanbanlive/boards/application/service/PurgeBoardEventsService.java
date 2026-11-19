package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.PurgeBoardEventsUseCase;
import com.kanbanlive.boards.application.port.out.BoardEventLog;
import com.kanbanlive.sharedkernel.application.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Sin @Transactional a proposito: cada lote es una transaccion corta del adapter, de modo que una purga grande
 * no mantiene locks largos ni compite con los escritores de eventos.
 */
@Service
class PurgeBoardEventsService implements PurgeBoardEventsUseCase {

    private final BoardEventLog log;
    private final Clock clock;

    PurgeBoardEventsService(BoardEventLog log, Clock clock) {
        this.log = log;
        this.clock = clock;
    }

    @Override
    public int execute(PurgeBoardEventsCommand command) {
        if (command.batchSize() <= 0 || command.maxBatches() <= 0 || command.retention().isNegative()) {
            throw new IllegalArgumentException("batchSize and maxBatches must be > 0 and retention >= 0");
        }
        Instant cutoff = clock.now().minus(command.retention());
        int total = 0;
        for (int batch = 0; batch < command.maxBatches(); batch++) {
            int deleted = log.deleteOlderThan(cutoff, command.batchSize());
            total += deleted;
            if (deleted < command.batchSize()) {
                break;
            }
        }
        return total;
    }
}
