package com.kanbanlive.platform.persistence;

import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Implementaciones de produccion de los puertos genericos del sharedkernel. */
@Configuration
class InfrastructureBeansConfig {

    @Bean
    Clock systemClock() {
        return Instant::now;
    }

    @Bean
    IdGenerator uuidGenerator() {
        return UUID::randomUUID;
    }
}
