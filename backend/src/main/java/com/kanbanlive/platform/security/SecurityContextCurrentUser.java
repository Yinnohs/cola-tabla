package com.kanbanlive.platform.security;

import com.kanbanlive.sharedkernel.application.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Adapter del puerto CurrentUser: lee el claim sub del JWT. Con kanban.security.enabled=false (solo desarrollo
 * local, sin token) devuelve un usuario fijo de desarrollo.
 */
@Component
class SecurityContextCurrentUser implements CurrentUser {

    private final boolean securityEnabled;
    private final String devUserId;

    SecurityContextCurrentUser(@Value("${kanban.security.enabled:true}") boolean securityEnabled,
            @Value("${kanban.security.dev-user-id:dev-user}") String devUserId) {
        this.securityEnabled = securityEnabled;
        this.devUserId = devUserId;
    }

    @Override
    public boolean unrestricted() {
        return !securityEnabled;
    }

    @Override
    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        if (!securityEnabled) {
            return devUserId;
        }
        throw new AuthenticationCredentialsNotFoundException("no authenticated user");
    }
}
