package com.kanbanlive.cards.adapter.in.web;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase.CreateCardCommand;
import com.kanbanlive.cards.application.port.in.DeleteCardUseCase;
import com.kanbanlive.cards.application.port.in.DeleteCardUseCase.DeleteCardCommand;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase.MoveCardCommand;
import com.kanbanlive.cards.application.port.in.UpdateCardUseCase;
import com.kanbanlive.cards.application.port.in.UpdateCardUseCase.UpdateCardCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cards")
@RestController
class CardController {

    private final CreateCardUseCase createCard;
    private final UpdateCardUseCase updateCard;
    private final MoveCardUseCase moveCard;
    private final DeleteCardUseCase deleteCard;

    CardController(CreateCardUseCase createCard, UpdateCardUseCase updateCard, MoveCardUseCase moveCard,
            DeleteCardUseCase deleteCard) {
        this.createCard = createCard;
        this.updateCard = updateCard;
        this.moveCard = moveCard;
        this.deleteCard = deleteCard;
    }

    @Operation(operationId = "createCard", summary = "Crear tarjeta al final de la lista")
    @ApiResponse(responseCode = "201")
    @PostMapping("/api/lists/{listId}/cards")
    ResponseEntity<CardResponse> create(@PathVariable UUID listId, @Valid @RequestBody CreateCardRequest request) {
        CardSummary summary = createCard
                .execute(new CreateCardCommand(listId, request.title(), request.description()));
        return ResponseEntity.created(URI.create("/api/cards/" + summary.id())).body(CardResponse.from(summary));
    }

    @Operation(operationId = "updateCard", summary = "Editar tarjeta (version optimista)")
    @PatchMapping("/api/cards/{cardId}")
    CardResponse update(@PathVariable UUID cardId, @Valid @RequestBody UpdateCardRequest request) {
        return CardResponse.from(updateCard.execute(
                new UpdateCardCommand(cardId, request.title(), request.description(), request.version())));
    }

    @Operation(operationId = "moveCard", summary = "Mover tarjeta: una sola fila actualizada")
    @PostMapping("/api/cards/{cardId}/move")
    CardResponse move(@PathVariable UUID cardId, @Valid @RequestBody MoveCardRequest request) {
        return CardResponse.from(moveCard.execute(
                new MoveCardCommand(cardId, request.listId(), request.prevCardId(), request.nextCardId())));
    }

    @Operation(operationId = "deleteCard", summary = "Borrar tarjeta")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/api/cards/{cardId}")
    ResponseEntity<Void> delete(@PathVariable UUID cardId) {
        deleteCard.execute(new DeleteCardCommand(cardId));
        return ResponseEntity.noContent().build();
    }
}
