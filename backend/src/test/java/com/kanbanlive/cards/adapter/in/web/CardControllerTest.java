package com.kanbanlive.cards.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase.CreateCardCommand;
import com.kanbanlive.cards.application.port.in.DeleteCardUseCase;
import com.kanbanlive.cards.application.port.in.DeleteCardUseCase.DeleteCardCommand;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase.MoveCardCommand;
import com.kanbanlive.cards.application.port.in.UpdateCardUseCase;
import com.kanbanlive.cards.application.port.in.UpdateCardUseCase.UpdateCardCommand;
import com.kanbanlive.platform.security.Perms;
import com.kanbanlive.platform.security.SecurityConfig;
import com.kanbanlive.platform.web.GlobalExceptionHandler;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.position.RebalanceRequiredException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CardControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean(name = "perms")
    Perms perms;

    @BeforeEach
    void allowAll() {
        when(perms.canView(any())).thenReturn(true);
        when(perms.canEdit(any())).thenReturn(true);
        when(perms.canAdmin(any())).thenReturn(true);
    }

    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CreateCardUseCase createCard;
    @MockitoBean
    UpdateCardUseCase updateCard;
    @MockitoBean
    MoveCardUseCase moveCard;
    @MockitoBean
    DeleteCardUseCase deleteCard;

    final UUID list = UUID.randomUUID();
    final UUID id = UUID.randomUUID();
    final CardSummary summary = new CardSummary(id, list, "Fix", "d", "V", 0, Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void postCreatesCardInList() throws Exception {
        when(createCard.execute(any())).thenReturn(summary);

        mvc.perform(post("/api/lists/{l}/cards", list).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Fix\",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/cards/" + id))
                .andExpect(jsonPath("$.listId").value(list.toString()))
                .andExpect(jsonPath("$.position").value("V"));
        verify(createCard).execute(new CreateCardCommand(list, "Fix", "d"));
    }

    @Test
    void postToMissingListIs404() throws Exception {
        when(createCard.execute(any())).thenThrow(new NotFoundException("list not found"));

        mvc.perform(post("/api/lists/{l}/cards", list).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Fix\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void postBlankTitleIs422() throws Exception {
        mvc.perform(post("/api/lists/{l}/cards", list).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void patchUpdatesWithVersion() throws Exception {
        when(updateCard.execute(any())).thenReturn(new CardSummary(id, list, "New", null, "V", 1, summary.createdAt()));

        mvc.perform(patch("/api/cards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.version").value(1));
        verify(updateCard).execute(new UpdateCardCommand(id, "New", null, 0));
    }

    @Test
    void patchStaleVersionIs409() throws Exception {
        when(updateCard.execute(any())).thenThrow(new StaleVersionException("stale"));

        mvc.perform(patch("/api/cards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void patchWithoutVersionIs422() throws Exception {
        mvc.perform(patch("/api/cards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void patchMissingCardIs404() throws Exception {
        when(updateCard.execute(any())).thenThrow(new NotFoundException("card not found"));

        mvc.perform(patch("/api/cards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveReturnsNewListPositionAndVersion() throws Exception {
        UUID target = UUID.randomUUID();
        UUID prev = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(moveCard.execute(any()))
                .thenReturn(new CardSummary(id, target, "Fix", "d", "Vk", 5, summary.createdAt()));

        mvc.perform(post("/api/cards/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listId\":\"" + target + "\",\"prevCardId\":\"" + prev + "\",\"nextCardId\":\""
                                + next + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listId").value(target.toString()))
                .andExpect(jsonPath("$.position").value("Vk"))
                .andExpect(jsonPath("$.version").value(5));
        verify(moveCard).execute(new MoveCardCommand(id, target, prev, next));
    }

    @Test
    void moveWithoutTargetListIs422() throws Exception {
        mvc.perform(post("/api/cards/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void moveWithBadNeighbourIs422() throws Exception {
        when(moveCard.execute(any())).thenThrow(new ValidationException("prev must be lower than next"));

        mvc.perform(post("/api/cards/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listId\":\"" + list + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void moveMissingCardOrListIs404() throws Exception {
        when(moveCard.execute(any())).thenThrow(new NotFoundException("not found"));

        mvc.perform(post("/api/cards/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listId\":\"" + list + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveWhenSpaceExhaustedIs409RebalanceRequired() throws Exception {
        when(moveCard.execute(any())).thenThrow(new RebalanceRequiredException("exhausted"));

        mvc.perform(post("/api/cards/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"listId\":\"" + list + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REBALANCE_REQUIRED"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/cards/{id}", id).with(jwt()).with(csrf())).andExpect(status().isNoContent());
        verify(deleteCard).execute(new DeleteCardCommand(id));
    }

    @Test
    void deleteMissingCardIs404() throws Exception {
        doThrow(new NotFoundException("card not found")).when(deleteCard).execute(any());

        mvc.perform(delete("/api/cards/{id}", id).with(jwt()).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(post("/api/lists/{l}/cards", list).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Fix\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/cards/{id}", id).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void deniedByPermsIs403() throws Exception {
        when(perms.canEdit(any())).thenReturn(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/cards/{id}", UUID.randomUUID()).with(jwt()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
