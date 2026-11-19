package com.kanbanlive.membership.adapter.in.web;

import com.kanbanlive.membership.application.port.in.ChangeRoleUseCase;
import com.kanbanlive.membership.application.port.in.ChangeRoleUseCase.ChangeRoleCommand;
import com.kanbanlive.membership.application.port.in.InviteMemberUseCase;
import com.kanbanlive.membership.application.port.in.InviteMemberUseCase.InviteMemberCommand;
import com.kanbanlive.membership.application.port.in.ListMembersUseCase;
import com.kanbanlive.membership.application.port.in.MembershipView;
import com.kanbanlive.membership.application.port.in.RemoveMemberUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final ListMembersUseCase listMembers;
    private final RemoveMemberUseCase removeMember;

    MembershipController(InviteMemberUseCase inviteMember, ChangeRoleUseCase changeRole,
            ListMembersUseCase listMembers, RemoveMemberUseCase removeMember) {
        this.inviteMember = inviteMember;
        this.changeRole = changeRole;
        this.listMembers = listMembers;
        this.removeMember = removeMember;
    }

    /** Solo OWNER (canAdmin): la lista expone ids de usuario y roles, y solo el OWNER gestiona miembros. */
    @Operation(operationId = "listMembers", summary = "Listar los miembros del workspace (solo OWNER)")
    @PreAuthorize("@perms.canAdmin(#workspaceId)")
    @GetMapping("/api/workspaces/{workspaceId}/members")
    List<MemberResponse> list(@PathVariable UUID workspaceId) {
        return listMembers.execute(workspaceId).stream().map(MemberResponse::from).toList();
    }

    @Operation(operationId = "removeMember", summary = "Quitar a un miembro del workspace (solo OWNER)")
    @ApiResponse(responseCode = "204")
    @PreAuthorize("@perms.canAdmin(#workspaceId)")
    @DeleteMapping("/api/workspaces/{workspaceId}/members/{userId}")
    ResponseEntity<Void> remove(@PathVariable UUID workspaceId, @PathVariable String userId) {
        removeMember.execute(workspaceId, userId);
        return ResponseEntity.noContent().build();
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
