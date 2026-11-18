package com.kanbanlive.membership.adapter.in.web;

import com.kanbanlive.membership.application.port.in.MembershipView;
import java.util.UUID;

record MemberResponse(UUID id, UUID workspaceId, String userId, String role, long version) {

    static MemberResponse from(MembershipView v) {
        return new MemberResponse(v.id(), v.workspaceId(), v.userId(), v.role(), v.version());
    }
}
