package com.kanbanlive.workspaces.adapter.in.web;

import com.kanbanlive.workspaces.application.port.in.CreateWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.GetWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.ListWorkspacesUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Workspaces")
@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController {

    private final CreateWorkspaceUseCase createWorkspace;
    private final ListWorkspacesUseCase listWorkspaces;
    private final GetWorkspaceUseCase getWorkspace;

    WorkspaceController(CreateWorkspaceUseCase createWorkspace, ListWorkspacesUseCase listWorkspaces,
            GetWorkspaceUseCase getWorkspace) {
        this.createWorkspace = createWorkspace;
        this.listWorkspaces = listWorkspaces;
        this.getWorkspace = getWorkspace;
    }

    @Operation(operationId = "createWorkspace", summary = "Crear workspace")
    @ApiResponse(responseCode = "201")
    @PostMapping
    ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceView view = createWorkspace.execute(request.toCommand());
        return ResponseEntity.created(URI.create("/api/workspaces/" + view.id())).body(WorkspaceResponse.from(view));
    }

    @Operation(operationId = "listWorkspaces", summary = "Listar workspaces")
    @GetMapping
    List<WorkspaceResponse> list() {
        return listWorkspaces.execute().stream().map(WorkspaceResponse::from).toList();
    }

    @Operation(operationId = "getWorkspace", summary = "Obtener un workspace")
    @GetMapping("/{workspaceId}")
    WorkspaceResponse get(@PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(getWorkspace.execute(workspaceId));
    }
}
