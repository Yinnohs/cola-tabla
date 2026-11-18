package com.kanbanlive.workspaces.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.platform.security.SecurityConfig;
import com.kanbanlive.platform.web.GlobalExceptionHandler;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.workspaces.application.port.in.CreateWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.GetWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.ListWorkspacesUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
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

@WebMvcTest(WorkspaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WorkspaceControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CreateWorkspaceUseCase createWorkspace;
    @MockitoBean
    ListWorkspacesUseCase listWorkspaces;
    @MockitoBean
    GetWorkspaceUseCase getWorkspace;

    final UUID id = UUID.randomUUID();
    final WorkspaceView view = new WorkspaceView(id, "Acme", 0, Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void postCreatesAndReturns201WithLocation() throws Exception {
        when(createWorkspace.execute(any())).thenReturn(view);

        mvc.perform(post("/api/workspaces").with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/workspaces/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void postBlankNameIs422WithCode() throws Exception {
        mvc.perform(post("/api/workspaces").with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void domainValidationErrorIs422() throws Exception {
        when(createWorkspace.execute(any())).thenThrow(new ValidationException("bad"));

        mvc.perform(post("/api/workspaces").with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void getListsWorkspaces() throws Exception {
        when(listWorkspaces.execute()).thenReturn(List.of(view));

        mvc.perform(get("/api/workspaces").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/workspaces")).andExpect(status().isUnauthorized());
    }

    @Test
    void getByIdReturnsWorkspace() throws Exception {
        when(getWorkspace.execute(id)).thenReturn(view);

        mvc.perform(get("/api/workspaces/{id}", id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void getByIdMissingIs404() throws Exception {
        when(getWorkspace.execute(id)).thenThrow(new NotFoundException("workspace not found"));

        mvc.perform(get("/api/workspaces/{id}", id).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getByIdWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/workspaces/{id}", id)).andExpect(status().isUnauthorized());
    }
}
