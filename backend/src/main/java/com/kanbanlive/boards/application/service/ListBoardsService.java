package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.application.port.in.ListBoardsUseCase;
import com.kanbanlive.boards.application.port.out.BoardRepository;
import com.kanbanlive.boards.application.port.out.WorkspaceDirectory;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListBoardsService implements ListBoardsUseCase {

    private final BoardRepository boards;
    private final WorkspaceDirectory workspaces;

    public ListBoardsService(BoardRepository boards, WorkspaceDirectory workspaces) {
        this.boards = boards;
        this.workspaces = workspaces;
    }

    @Override
    public List<BoardSummary> execute(UUID workspaceId) {
        if (!workspaces.exists(workspaceId)) {
            throw new NotFoundException("workspace " + workspaceId + " not found");
        }
        return boards.findByWorkspaceId(workspaceId).stream().map(BoardSummaries::of).toList();
    }
}
