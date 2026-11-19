package com.kanbanlive.boards.adapter.in.job;

import com.kanbanlive.boards.application.port.in.PurgeBoardEventsUseCase;
import com.kanbanlive.boards.application.port.in.PurgeBoardEventsUseCase.PurgeBoardEventsCommand;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purga periodica del log board_event (B4.3). Solo se dispara si el scheduling esta activo
 * ({@code kanban.scheduling.enabled}, por defecto si; apagado en tests). Retencion {@code kanban.events.retention}
 * (24h), lotes acotados. Un fallo no mata el planificador: se registra y se reintenta en el siguiente ciclo.
 */
@Component
class BoardEventPurgeJob {

    private static final Logger LOG = LoggerFactory.getLogger(BoardEventPurgeJob.class);

    private final PurgeBoardEventsUseCase purge;
    private final Duration retention;
    private final int batchSize;
    private final int maxBatches;

    BoardEventPurgeJob(PurgeBoardEventsUseCase purge,
            @Value("${kanban.events.retention:PT24H}") Duration retention,
            @Value("${kanban.events.purge.batch-size:1000}") int batchSize,
            @Value("${kanban.events.purge.max-batches:100}") int maxBatches) {
        this.purge = purge;
        this.retention = retention;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
    }

    @Scheduled(fixedDelayString = "${kanban.events.purge.interval:PT1H}",
            initialDelayString = "${kanban.events.purge.initial-delay:PT1M}")
    void run() {
        try {
            int deleted = purge.execute(new PurgeBoardEventsCommand(retention, batchSize, maxBatches));
            if (deleted > 0) {
                LOG.info("Purged {} board events older than {}", deleted, retention);
            }
        } catch (RuntimeException e) {
            LOG.warn("Board event purge failed; will retry on the next cycle", e);
        }
    }
}
