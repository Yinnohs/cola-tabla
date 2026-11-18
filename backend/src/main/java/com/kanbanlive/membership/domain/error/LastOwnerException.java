package com.kanbanlive.membership.domain.error;

import com.kanbanlive.sharedkernel.domain.ConflictException;

/** Un workspace no puede quedarse sin OWNER. */
public class LastOwnerException extends ConflictException {

    public LastOwnerException(String message) {
        super("LAST_OWNER", message);
    }
}
