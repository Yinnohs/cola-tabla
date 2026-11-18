package com.kanbanlive.lists.adapter.in.web;

import com.kanbanlive.lists.application.port.in.CreateListUseCase;
import com.kanbanlive.lists.application.port.in.CreateListUseCase.CreateListCommand;
import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.in.MoveListUseCase;
import com.kanbanlive.lists.application.port.in.MoveListUseCase.MoveListCommand;
import com.kanbanlive.lists.application.port.in.RenameListUseCase;
import com.kanbanlive.lists.application.port.in.RenameListUseCase.RenameListCommand;
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

@Tag(name = "Lists")
@RestController
class ListController {

    private final CreateListUseCase createList;
    private final RenameListUseCase renameList;
    private final MoveListUseCase moveList;

    ListController(CreateListUseCase createList, RenameListUseCase renameList, MoveListUseCase moveList) {
        this.createList = createList;
        this.renameList = renameList;
        this.moveList = moveList;
    }

    @Operation(operationId = "createList", summary = "Crear lista al final del tablero")
    @ApiResponse(responseCode = "201")
    @PreAuthorize("@perms.canEdit(#boardId)")
    @PostMapping("/api/boards/{boardId}/lists")
    ResponseEntity<ListResponse> create(@PathVariable UUID boardId, @Valid @RequestBody CreateListRequest request) {
        ListSummary summary = createList.execute(new CreateListCommand(boardId, request.name()));
        return ResponseEntity.created(URI.create("/api/lists/" + summary.id())).body(ListResponse.from(summary));
    }

    @Operation(operationId = "renameList", summary = "Renombrar lista (version optimista)")
    @PreAuthorize("@perms.canEdit(#listId)")
    @PatchMapping("/api/lists/{listId}")
    ListResponse rename(@PathVariable UUID listId, @Valid @RequestBody RenameListRequest request) {
        return ListResponse.from(
                renameList.execute(new RenameListCommand(listId, request.name(), request.version())));
    }

    @Operation(operationId = "moveList", summary = "Mover lista entre vecinas (prev/next)")
    @PreAuthorize("@perms.canEdit(#listId)")
    @PostMapping("/api/lists/{listId}/move")
    ListResponse move(@PathVariable UUID listId, @RequestBody MoveListRequest request) {
        return ListResponse.from(
                moveList.execute(new MoveListCommand(listId, request.prevListId(), request.nextListId())));
    }
}
