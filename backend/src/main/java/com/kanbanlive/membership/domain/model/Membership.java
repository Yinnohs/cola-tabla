package com.kanbanlive.membership.domain.model;

import com.kanbanlive.sharedkernel.domain.AggregateRoot;
import com.kanbanlive.sharedkernel.domain.Validations;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Pertenencia de un usuario a un workspace con un rol. Unica por (workspace, usuario). */
public final class Membership extends AggregateRoot<UUID> {

    public static final int USER_ID_MAX = 255;

    private final UUID workspaceId;
    private final String userId;
    private Role role;
    private final Instant createdAt;
    private Instant updatedAt;

    private Membership(UUID id, UUID workspaceId, String userId, Role role, long version, Instant createdAt,
            Instant updatedAt) {
        super(id, version);
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Membership create(UUID id, UUID workspaceId, String userId, Role role, Instant now) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(role, "role");
        return new Membership(id, workspaceId, Validations.requireText(userId, "userId", USER_ID_MAX), role, 0, now,
                now);
    }

    public static Membership rehydrate(UUID id, UUID workspaceId, String userId, Role role, long version,
            Instant createdAt, Instant updatedAt) {
        return new Membership(id, workspaceId, userId, role, version, createdAt, updatedAt);
    }

    public void changeRole(Role newRole, Instant now) {
        this.role = Objects.requireNonNull(newRole, "role");
        this.updatedAt = now;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public String userId() {
        return userId;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
