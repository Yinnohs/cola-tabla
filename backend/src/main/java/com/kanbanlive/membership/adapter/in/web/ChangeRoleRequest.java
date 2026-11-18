package com.kanbanlive.membership.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

record ChangeRoleRequest(@NotBlank String role) {
}
