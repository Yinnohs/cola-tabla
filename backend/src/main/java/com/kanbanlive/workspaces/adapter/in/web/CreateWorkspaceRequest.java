package com.kanbanlive.workspaces.adapter.in.web;

import com.kanbanlive.workspaces.application.port.in.CreateWorkspaceUseCase.CreateWorkspaceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateWorkspaceRequest(@NotBlank @Size(max = 120) String name) {

    CreateWorkspaceCommand toCommand() {
        return new CreateWorkspaceCommand(name);
    }
}
