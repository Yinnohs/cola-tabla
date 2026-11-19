package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.GetUserRolesUseCase;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.model.Membership;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetUserRolesService implements GetUserRolesUseCase {

    private final MembershipRepository repository;

    public GetUserRolesService(MembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<UUID, String> execute(String userId) {
        Map<UUID, String> roles = new HashMap<>();
        for (Membership m : repository.findByUser(userId)) {
            roles.put(m.workspaceId(), m.role().name());
        }
        return roles;
    }
}
