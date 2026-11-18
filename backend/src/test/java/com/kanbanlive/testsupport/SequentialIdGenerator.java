package com.kanbanlive.testsupport;

import com.kanbanlive.sharedkernel.application.IdGenerator;
import java.util.UUID;

/** Ids deterministas: 00000000-0000-0000-0000-000000000001, ...002, ... */
public final class SequentialIdGenerator implements IdGenerator {

    private long next = 1;

    public static UUID id(long n) {
        return new UUID(0L, n);
    }

    @Override
    public UUID newId() {
        return id(next++);
    }
}
