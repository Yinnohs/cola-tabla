package com.kanbanlive.testsupport;

import com.kanbanlive.sharedkernel.application.Clock;
import java.time.Instant;

/** Reloj controlable para tests de aplicacion. */
public final class FixedClock implements Clock {

    private Instant now;

    public FixedClock(Instant now) {
        this.now = now;
    }

    public void set(Instant now) {
        this.now = now;
    }

    @Override
    public Instant now() {
        return now;
    }
}
