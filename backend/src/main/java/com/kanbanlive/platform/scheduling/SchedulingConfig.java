package com.kanbanlive.platform.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa @Scheduled (purga del log de eventos, B4.3). Desactivable con {@code kanban.scheduling.enabled=false}
 * (los tests lo apagan para no tener tareas en segundo plano salvo el test del job).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "kanban.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
