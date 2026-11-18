package com.kanbanlive.boards.adapter.in.web;

import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.application.port.in.CreateBoardUseCase;
import com.kanbanlive.boards.application.port.in.CreateBoardUseCase.CreateBoardCommand;
import com.kanbanlive.boards.application.port.in.GetBoardUseCase;
import com.kanbanlive.boards.application.port.in.ListBoardsUseCase;
import com.kanbanlive.boards.application.port.in.RenameBoardUseCase;
import com.kanbanlive.boards.application.port.in.RenameBoardUseCase.RenameBoardCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Boards")
@RestController
class BoardController {

    private final CreateBoardUseCase createBoard;
    private final GetBoardUseCase getBoard;
    private final RenameBoardUseCase renameBoard;
    private final ListBoardsUseCase listBoards;

    BoardController(CreateBoardUseCase createBoard, GetBoardUseCase getBoard, RenameBoardUseCase renameBoard,
            ListBoardsUseCase listBoards) {
        this.listBoards = listBoards;
        this.createBoard = createBoard;
        this.getBoard = getBoard;
        this.renameBoard = renameBoard;
    }

    @Operation(operationId = "createBoard", summary = "Crear tablero en un workspace")
    @ApiResponse(responseCode = "201")
    @PostMapping("/api/workspaces/{workspaceId}/boards")
    ResponseEntity<BoardResponse> create(@PathVariable UUID workspaceId,
            @Valid @RequestBody CreateBoardRequest request) {
        BoardSummary summary = createBoard.execute(new CreateBoardCommand(workspaceId, request.name()));
        return ResponseEntity.created(URI.create("/api/boards/" + summary.id())).body(BoardResponse.from(summary));
    }

    @Operation(operationId = "listBoards", summary = "Listar tableros de un workspace")
    @GetMapping("/api/workspaces/{workspaceId}/boards")
    List<BoardResponse> list(@PathVariable UUID workspaceId) {
        return listBoards.execute(workspaceId).stream().map(BoardResponse::from).toList();
    }

    @Operation(operationId = "getBoard", summary = "Tablero con listas y tarjetas ordenadas")
    @GetMapping("/api/boards/{boardId}")
    BoardDetailsResponse get(@PathVariable UUID boardId) {
        return BoardDetailsResponse.from(getBoard.execute(boardId));
    }

    @Operation(operationId = "renameBoard", summary = "Renombrar tablero (version optimista)")
    @PatchMapping("/api/boards/{boardId}")
    BoardResponse rename(@PathVariable UUID boardId, @Valid @RequestBody RenameBoardRequest request) {
        return BoardResponse.from(
                renameBoard.execute(new RenameBoardCommand(boardId, request.name(), request.version())));
    }
}
