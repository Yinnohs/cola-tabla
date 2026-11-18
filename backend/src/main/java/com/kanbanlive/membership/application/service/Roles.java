package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.sharedkernel.domain.ValidationException;

final class Roles {

    private Roles() {
    }

    static Role parse(String value) {
        try {
            return Role.valueOf(value == null ? "" : value.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("role must be one of OWNER, EDITOR, VIEWER");
        }
    }
}
