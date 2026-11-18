package com.kanbanlive.membership.domain.error;

import com.kanbanlive.sharedkernel.domain.ConflictException;

public class AlreadyMemberException extends ConflictException {

    public AlreadyMemberException(String message) {
        super("ALREADY_MEMBER", message);
    }
}
