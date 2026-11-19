package com.kanbanlive.collaboration.adapter.in.web;

import com.kanbanlive.collaboration.application.port.in.GetBoardEventsSinceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Collaboration")
@RestController
class BoardEventsController {

    private final GetBoardEventsSinceUseCase getEvents;

    BoardEventsController(GetBoardEventsSinceUseCase getEvents) {
        this.getEvents = getEvents;
    }

    @Operation(operationId = "getBoardEvents",
            summary = "Eventos del tablero con seq > since (resync tras una desconexion)")
    @ApiResponse(responseCode = "400", description = "since negativo o mayor que el seq actual (code INVALID_SINCE)")
    @ApiResponse(responseCode = "410", description = "Demasiado atrasado o log purgado: recargar el tablero (code EVENTS_GONE)")
    @PreAuthorize("@perms.canView(#boardId)")
    @GetMapping("/api/boards/{boardId}/events")
    BoardEventsResponse events(@PathVariable UUID boardId, @RequestParam long since) {
        return BoardEventsResponse.from(getEvents.execute(boardId, since));
    }
}
