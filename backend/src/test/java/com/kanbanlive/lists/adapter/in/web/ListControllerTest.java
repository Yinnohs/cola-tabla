package com.kanbanlive.lists.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.lists.application.port.in.CreateListUseCase;
import com.kanbanlive.lists.application.port.in.CreateListUseCase.CreateListCommand;
import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.in.MoveListUseCase;
import com.kanbanlive.lists.application.port.in.MoveListUseCase.MoveListCommand;
import com.kanbanlive.lists.application.port.in.RenameListUseCase;
import com.kanbanlive.lists.application.port.in.RenameListUseCase.RenameListCommand;
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

@WebMvcTest(ListController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ListControllerTest {

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
    CreateListUseCase createList;
    @MockitoBean
    RenameListUseCase renameList;
    @MockitoBean
    MoveListUseCase moveList;

    final UUID board = UUID.randomUUID();
    final UUID id = UUID.randomUUID();
    final ListSummary summary = new ListSummary(id, board, "Todo", "V", 0, Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void postCreatesListInBoard() throws Exception {
        when(createList.execute(any())).thenReturn(summary);

        mvc.perform(post("/api/boards/{b}/lists", board).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Todo\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/lists/" + id))
                .andExpect(jsonPath("$.boardId").value(board.toString()))
                .andExpect(jsonPath("$.position").value("V"))
                .andExpect(jsonPath("$.version").value(0));
        verify(createList).execute(new CreateListCommand(board, "Todo"));
    }

    @Test
    void postToMissingBoardIs404() throws Exception {
        when(createList.execute(any())).thenThrow(new NotFoundException("board not found"));

        mvc.perform(post("/api/boards/{b}/lists", board).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Todo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void postBlankNameIs422() throws Exception {
        mvc.perform(post("/api/boards/{b}/lists", board).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void patchRenamesWithVersion() throws Exception {
        when(renameList.execute(any())).thenReturn(new ListSummary(id, board, "Doing", "V", 1, summary.createdAt()));

        mvc.perform(patch("/api/lists/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Doing\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Doing"))
                .andExpect(jsonPath("$.version").value(1));
        verify(renameList).execute(new RenameListCommand(id, "Doing", 0));
    }

    @Test
    void patchStaleVersionIs409() throws Exception {
        when(renameList.execute(any())).thenThrow(new StaleVersionException("stale"));

        mvc.perform(patch("/api/lists/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Doing\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void patchWithoutVersionIs422() throws Exception {
        mvc.perform(patch("/api/lists/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Doing\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void patchMissingListIs404() throws Exception {
        when(renameList.execute(any())).thenThrow(new NotFoundException("list not found"));

        mvc.perform(patch("/api/lists/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Doing\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveReturnsNewPositionAndVersion() throws Exception {
        UUID prev = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(moveList.execute(any())).thenReturn(new ListSummary(id, board, "Todo", "Vk", 3, summary.createdAt()));

        mvc.perform(post("/api/lists/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prevListId\":\"" + prev + "\",\"nextListId\":\"" + next + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("Vk"))
                .andExpect(jsonPath("$.version").value(3));
        verify(moveList).execute(new MoveListCommand(id, prev, next));
    }

    @Test
    void moveWithoutNeighboursMeansExtreme() throws Exception {
        when(moveList.execute(any())).thenReturn(summary);

        mvc.perform(post("/api/lists/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(moveList).execute(new MoveListCommand(id, null, null));
    }

    @Test
    void moveWithBadNeighbourIs422() throws Exception {
        when(moveList.execute(any())).thenThrow(new ValidationException("prev must be lower than next"));

        mvc.perform(post("/api/lists/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void moveMissingListIs404() throws Exception {
        when(moveList.execute(any())).thenThrow(new NotFoundException("list not found"));

        mvc.perform(post("/api/lists/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveWhenSpaceExhaustedIs409RebalanceRequired() throws Exception {
        when(moveList.execute(any())).thenThrow(new RebalanceRequiredException("exhausted"));

        mvc.perform(post("/api/lists/{id}/move", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REBALANCE_REQUIRED"));
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(post("/api/boards/{b}/lists", board).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Todo\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/lists/{id}/move", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniedByPermsIs403() throws Exception {
        when(perms.canEdit(any())).thenReturn(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/lists/{id}", UUID.randomUUID()).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
