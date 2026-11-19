package com.kanbanlive.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kanbanlive.testsupport.SecuredApiIT;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** B2.6 - VIEWER solo lee, EDITOR escribe pero no administra, OWNER hace todo. */
class RolePermissionsIT extends SecuredApiIT {

    String owner;
    String editor;
    String viewer;
    UUID ws;
    UUID board;
    UUID list;
    UUID card;

    @BeforeEach
    void fixture() throws Exception {
        owner = user("owner");
        editor = user("editor");
        viewer = user("viewer");
        ws = createWorkspace(owner, "Acme");
        board = createBoard(owner, ws);
        list = createList(owner, board);
        card = createCard(owner, list);
        invite(owner, ws, editor, "EDITOR");
        invite(owner, ws, viewer, "VIEWER");
    }

    private static String json(String s) {
        return s;
    }

    @Test
    void viewerCanReadButNotWrite() throws Exception {
        mvc.perform(get("/api/workspaces/{id}", ws).with(as(viewer))).andExpect(status().isOk());
        mvc.perform(get("/api/workspaces/{id}/boards", ws).with(as(viewer))).andExpect(status().isOk());
        mvc.perform(get("/api/boards/{id}", board).with(as(viewer))).andExpect(status().isOk());

        mvc.perform(post("/api/workspaces/{id}/boards", ws).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"n\"}"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/boards/{id}", board).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"n\",\"version\":0}"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/boards/{id}/lists", board).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"n\"}"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/lists/{id}/cards", list).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"title\":\"t\"}"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/cards/{id}/move", card).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"listId\":\"" + list + "\"}"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/cards/{id}", card).with(as(viewer))).andExpect(status().isForbidden());
    }

    @Test
    void editorCanWriteButNotAdminister() throws Exception {
        mvc.perform(post("/api/workspaces/{id}/boards", ws).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"n\"}"))).andExpect(status().isCreated());
        mvc.perform(post("/api/boards/{id}/lists", board).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"n\"}"))).andExpect(status().isCreated());
        mvc.perform(post("/api/lists/{id}/cards", list).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"title\":\"t\"}"))).andExpect(status().isCreated());
        mvc.perform(delete("/api/cards/{id}", card).with(as(editor))).andExpect(status().isNoContent());

        mvc.perform(post("/api/workspaces/{id}/members", ws).with(as(editor)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"userId\":\"x\",\"role\":\"VIEWER\"}"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/workspaces/{id}/members/{u}", ws, viewer).with(as(editor))
                .contentType(MediaType.APPLICATION_JSON).content(json("{\"role\":\"OWNER\"}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanAdministerMembers() throws Exception {
        String newcomer = user("newcomer");
        mvc.perform(post("/api/workspaces/{id}/members", ws).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"userId\":\"" + newcomer + "\",\"role\":\"EDITOR\"}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("EDITOR"));
        mvc.perform(post("/api/workspaces/{id}/members", ws).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"userId\":\"" + newcomer + "\",\"role\":\"EDITOR\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
        mvc.perform(post("/api/workspaces/{id}/members", ws).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"userId\":\"y\",\"role\":\"BOSS\"}"))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void changingRoleTakesEffectAndLastOwnerIsProtected() throws Exception {
        // VIEWER -> EDITOR: ya puede escribir.
        mvc.perform(patch("/api/workspaces/{id}/members/{u}", ws, viewer).with(as(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json("{\"role\":\"EDITOR\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("EDITOR"));
        mvc.perform(post("/api/boards/{id}/lists", board).with(as(viewer)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"name\":\"n\"}"))).andExpect(status().isCreated());

        // El unico OWNER no puede degradarse.
        mvc.perform(patch("/api/workspaces/{id}/members/{u}", ws, owner).with(as(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json("{\"role\":\"EDITOR\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_OWNER"));
    }

    @Test
    void ownerListsMembersAndOthersCannot() throws Exception {
        mvc.perform(get("/api/workspaces/{id}/members", ws).with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.userId=='" + editor + "')].role").value("EDITOR"));
        mvc.perform(get("/api/workspaces/{id}/members", ws).with(as(editor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/workspaces/{id}/members", ws).with(as(viewer))).andExpect(status().isForbidden());
    }

    @Test
    void ownerRemovesMembersButNotTheLastOwner() throws Exception {
        mvc.perform(delete("/api/workspaces/{id}/members/{u}", ws, viewer).with(as(editor)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/workspaces/{id}/members/{u}", ws, viewer).with(as(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/workspaces/{id}", ws).with(as(viewer))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/workspaces/{id}/members/{u}", ws, viewer).with(as(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/workspaces/{id}/members/{u}", ws, owner).with(as(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_OWNER"));
    }

    @Test
    void workspaceExposesTheRoleOfTheCurrentUser() throws Exception {
        mvc.perform(get("/api/workspaces/{id}", ws).with(as(owner))).andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(get("/api/workspaces/{id}", ws).with(as(editor))).andExpect(jsonPath("$.role").value("EDITOR"));
        mvc.perform(get("/api/workspaces/{id}", ws).with(as(viewer))).andExpect(jsonPath("$.role").value("VIEWER"));
        mvc.perform(get("/api/workspaces").with(as(viewer))).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("VIEWER"));
    }

    @Test
    void workspaceCreatorIsOwner() throws Exception {
        mvc.perform(post("/api/workspaces/{id}/members", ws).with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"userId\":\"z\",\"role\":\"VIEWER\"}"))).andExpect(status().isCreated());
    }
}
