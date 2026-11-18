package com.kanbanlive.membership.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/** role: OWNER, EDITOR o VIEWER. */
record InviteMemberRequest(@NotBlank String userId, @NotBlank String role) {
}
