package com.kanbanlive.boards.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import com.kanbanlive.boards.application.port.in.BoardDetails.CardDetails;
import com.kanbanlive.boards.application.port.in.BoardDetails.ListDetails;
import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.application.port.in.CreateBoardUseCase;
import com.kanbanlive.boards.application.port.in.CreateBoardUseCase.CreateBoardCommand;
import com.kanbanlive.boards.application.port.in.GetBoardUseCase;
import com.kanbanlive.boards.application.port.in.RenameBoardUseCase;
import com.kanbanlive.boards.application.port.in.RenameBoardUseCase.RenameBoardCommand;
import com.kanbanlive.platform.security.SecurityConfig;
import com.kanbanlive.platform.web.GlobalExceptionHandler;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BoardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class BoardControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CreateBoardUseCase createBoard;
    @MockitoBean
    GetBoardUseCase getBoard;
    @MockitoBean
    RenameBoardUseCase renameBoard;

    final UUID ws = UUID.randomUUID();
    final UUID id = UUID.randomUUID();
    final BoardSummary summary = new BoardSummary(id, ws, "Sprint", 0, Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void postCreatesBoardInWorkspace() throws Exception {
        when(createBoard.execute(any())).thenReturn(summary);

        mvc.perform(post("/api/workspaces/{ws}/boards", ws).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sprint\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/boards/" + id))
                .andExpect(jsonPath("$.workspaceId").value(ws.toString()));
        verify(createBoard).execute(new CreateBoardCommand(ws, "Sprint"));
    }

    @Test
    void postToMissingWorkspaceIs404() throws Exception {
        when(createBoard.execute(any())).thenThrow(new NotFoundException("workspace not found"));

        mvc.perform(post("/api/workspaces/{ws}/boards", ws).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sprint\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getReturnsBoardWithListsAndCards() throws Exception {
        UUID list = UUID.randomUUID();
        UUID card = UUID.randomUUID();
        when(getBoard.execute(id)).thenReturn(new BoardDetails(id, ws, "Sprint", 2, List.of(
                new ListDetails(list, "Todo", "V", 1, List.of(new CardDetails(card, "T", null, "V", 0))))));

        mvc.perform(get("/api/boards/{id}", id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.lists[0].name").value("Todo"))
                .andExpect(jsonPath("$.lists[0].cards[0].title").value("T"));
    }

    @Test
    void getMissingBoardIs404() throws Exception {
        when(getBoard.execute(id)).thenThrow(new NotFoundException("board not found"));

        mvc.perform(get("/api/boards/{id}", id).with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void patchRenamesWithVersion() throws Exception {
        when(renameBoard.execute(any())).thenReturn(new BoardSummary(id, ws, "New", 1, summary.createdAt()));

        mvc.perform(patch("/api/boards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.version").value(1));
        verify(renameBoard).execute(new RenameBoardCommand(id, "New", 0));
    }

    @Test
    void patchStaleVersionIs409() throws Exception {
        when(renameBoard.execute(any())).thenThrow(new StaleVersionException("stale"));

        mvc.perform(patch("/api/boards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void patchWithoutVersionIs422() throws Exception {
        mvc.perform(patch("/api/boards/{id}", id).with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/boards/{id}", id)).andExpect(status().isUnauthorized());
    }
}
