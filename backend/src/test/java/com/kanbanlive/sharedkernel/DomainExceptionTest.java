package com.kanbanlive.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.sharedkernel.domain.ConflictException;
import com.kanbanlive.sharedkernel.domain.DomainException;
import com.kanbanlive.sharedkernel.domain.ForbiddenException;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void subtypesCarryStableCodesAndMessages() {
        assertThat(new NotFoundException("x").code()).isEqualTo("NOT_FOUND");
        assertThat(new ConflictException("x").code()).isEqualTo("CONFLICT");
        assertThat(new StaleVersionException("x").code()).isEqualTo("STALE_VERSION");
        assertThat(new ForbiddenException("x").code()).isEqualTo("FORBIDDEN");
        assertThat(new ValidationException("boom")).hasMessage("boom").isInstanceOf(DomainException.class);
    }

    @Test
    void staleVersionIsAConflict() {
        assertThat(new StaleVersionException("x")).isInstanceOf(ConflictException.class);
    }
}
