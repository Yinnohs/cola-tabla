package com.kanbanlive.membership.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.membership.application.port.in.ChangeRoleUseCase.ChangeRoleCommand;
import com.kanbanlive.membership.application.port.in.CheckAccessUseCase.Permission;
import com.kanbanlive.membership.application.port.in.InviteMemberUseCase.InviteMemberCommand;
import com.kanbanlive.membership.application.port.out.ResourceWorkspaceLookup;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.error.AlreadyMemberException;
import com.kanbanlive.membership.domain.error.LastOwnerException;
import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.testsupport.FixedClock;
import com.kanbanlive.testsupport.SequentialIdGenerator;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipServicesTest {

    static final class InMemoryMembershipRepository implements MembershipRepository {
        final Map<String, Membership> store = new HashMap<>();

        static String key(UUID workspaceId, String userId) {
            return workspaceId + "/" + userId;
        }

        @Override
        public Membership save(Membership m) {
            store.put(key(m.workspaceId(), m.userId()), m);
            return m;
        }

        @Override
        public Optional<Membership> findByWorkspaceAndUser(UUID workspaceId, String userId) {
            return Optional.ofNullable(store.get(key(workspaceId, userId)));
        }

        @Override
        public java.util.Set<UUID> findWorkspaceIdsByUser(String userId) {
            return store.values().stream().filter(m -> m.userId().equals(userId)).map(Membership::workspaceId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public long countByWorkspaceAndRole(UUID workspaceId, Role role) {
            return store.values().stream()
                    .filter(m -> m.workspaceId().equals(workspaceId) && m.role() == role).count();
        }
    }

    final InMemoryMembershipRepository repo = new InMemoryMembershipRepository();
    final Map<UUID, UUID> boardToWorkspace = new HashMap<>();
    final ResourceWorkspaceLookup boards = id -> Optional.ofNullable(boardToWorkspace.get(id));
    final FixedClock clock = new FixedClock(Instant.parse("2026-01-01T00:00:00Z"));
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final AssignWorkspaceOwnerService assign = new AssignWorkspaceOwnerService(repo, ids, clock);
    final InviteMemberService invite = new InviteMemberService(repo, ids, clock);
    final ChangeRoleService changeRole = new ChangeRoleService(repo, clock);
    final CheckAccessService access = new CheckAccessService(repo, boards);
    final UUID ws = UUID.randomUUID();

    @Test
    void assignOwnerIsIdempotentAndPromotes() {
        assign.execute(ws, "u1");
        assign.execute(ws, "u1");
        assertThat(repo.store).hasSize(1);
        assertThat(repo.findByWorkspaceAndUser(ws, "u1").orElseThrow().role()).isEqualTo(Role.OWNER);

        invite.execute(new InviteMemberCommand(ws, "u2", "viewer"));
        assign.execute(ws, "u2");
        assertThat(repo.findByWorkspaceAndUser(ws, "u2").orElseThrow().role()).isEqualTo(Role.OWNER);
    }

    @Test
    void inviteCreatesMembershipAndRejectsDuplicatesAndBadRole() {
        var view = invite.execute(new InviteMemberCommand(ws, "u2", "EDITOR"));
        assertThat(view.role()).isEqualTo("EDITOR");
        assertThatThrownBy(() -> invite.execute(new InviteMemberCommand(ws, "u2", "VIEWER")))
                .isInstanceOf(AlreadyMemberException.class);
        assertThatThrownBy(() -> invite.execute(new InviteMemberCommand(ws, "u3", "BOSS")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void changeRoleUpdatesAndProtectsLastOwner() {
        assign.execute(ws, "u1");
        invite.execute(new InviteMemberCommand(ws, "u2", "VIEWER"));

        assertThat(changeRole.execute(new ChangeRoleCommand(ws, "u2", "EDITOR")).role()).isEqualTo("EDITOR");
        assertThatThrownBy(() -> changeRole.execute(new ChangeRoleCommand(ws, "u1", "VIEWER")))
                .isInstanceOf(LastOwnerException.class);

        changeRole.execute(new ChangeRoleCommand(ws, "u2", "OWNER"));
        assertThat(changeRole.execute(new ChangeRoleCommand(ws, "u1", "VIEWER")).role()).isEqualTo("VIEWER");
        assertThatThrownBy(() -> changeRole.execute(new ChangeRoleCommand(ws, "ghost", "VIEWER")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsWorkspacesOfUser() {
        UUID other = UUID.randomUUID();
        assign.execute(ws, "u1");
        invite.execute(new InviteMemberCommand(other, "u2", "VIEWER"));
        assertThat(new ListUserWorkspacesService(repo).execute("u1")).containsExactly(ws);
        assertThat(new ListUserWorkspacesService(repo).execute("nobody")).isEmpty();
    }

    @Test
    void accessFollowsRoleAndResolvesBoards() {
        UUID board = UUID.randomUUID();
        boardToWorkspace.put(board, ws);
        assign.execute(ws, "owner");
        invite.execute(new InviteMemberCommand(ws, "editor", "EDITOR"));
        invite.execute(new InviteMemberCommand(ws, "viewer", "VIEWER"));

        assertThat(access.execute("owner", ws, Permission.ADMIN)).isTrue();
        assertThat(access.execute("editor", board, Permission.EDIT)).isTrue();
        assertThat(access.execute("editor", board, Permission.ADMIN)).isFalse();
        assertThat(access.execute("viewer", board, Permission.VIEW)).isTrue();
        assertThat(access.execute("viewer", board, Permission.EDIT)).isFalse();
        assertThat(access.execute("stranger", board, Permission.VIEW)).isFalse();
        assertThat(access.execute("owner", UUID.randomUUID(), Permission.VIEW)).isFalse();
    }
}
