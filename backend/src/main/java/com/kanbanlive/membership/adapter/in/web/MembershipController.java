package com.kanbanlive.membership.adapter.in.web;

import com.kanbanlive.membership.application.port.in.ChangeRoleUseCase;
import com.kanbanlive.membership.application.port.in.ChangeRoleUseCase.ChangeRoleCommand;
import com.kanbanlive.membership.application.port.in.InviteMemberUseCase;
import com.kanbanlive.membership.application.port.in.InviteMemberUseCase.InviteMemberCommand;
import com.kanbanlive.membership.application.port.in.MembershipView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Membership")
@RestController
class MembershipController {

    private final InviteMemberUseCase inviteMember;
    private final ChangeRoleUseCase changeRole;

    MembershipController(InviteMemberUseCase inviteMember, ChangeRoleUseCase changeRole) {
        this.inviteMember = inviteMember;
        this.changeRole = changeRole;
    }

    @Operation(operationId = "inviteMember", summary = "Invitar a un usuario al workspace (solo OWNER)")
    @ApiResponse(responseCode = "201")
    @PreAuthorize("@perms.canAdmin(#workspaceId)")
    @PostMapping("/api/workspaces/{workspaceId}/members")
    ResponseEntity<MemberResponse> invite(@PathVariable UUID workspaceId,
            @Valid @RequestBody InviteMemberRequest request) {
        MembershipView view = inviteMember.execute(
                new InviteMemberCommand(workspaceId, request.userId(), request.role()));
        return ResponseEntity.created(URI.create("/api/workspaces/" + workspaceId + "/members/" + view.userId()))
                .body(MemberResponse.from(view));
    }

    @Operation(operationId = "changeMemberRole", summary = "Cambiar el rol de un miembro (solo OWNER)")
    @PreAuthorize("@perms.canAdmin(#workspaceId)")
    @PatchMapping("/api/workspaces/{workspaceId}/members/{userId}")
    MemberResponse changeRole(@PathVariable UUID workspaceId, @PathVariable String userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return MemberResponse.from(changeRole.execute(new ChangeRoleCommand(workspaceId, userId, request.role())));
    }
}
