package com.kanbanlive.sharedkernel.application;

import java.time.Instant;

/** Puerto de tiempo para tests deterministas. */
public interface Clock {

    Instant now();
}
