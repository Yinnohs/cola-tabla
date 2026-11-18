package com.kanbanlive.membership.application.port.in;

import java.util.UUID;

public record MembershipView(UUID id, UUID workspaceId, String userId, String role, long version) {
}
