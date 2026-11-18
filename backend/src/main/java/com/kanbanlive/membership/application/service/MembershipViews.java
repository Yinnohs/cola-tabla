package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.MembershipView;
import com.kanbanlive.membership.domain.model.Membership;

final class MembershipViews {

    private MembershipViews() {
    }

    static MembershipView of(Membership m) {
        return new MembershipView(m.id(), m.workspaceId(), m.userId(), m.role().name(), m.version());
    }
}
