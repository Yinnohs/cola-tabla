package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.application.port.in.CreateBoardUseCase;
import com.kanbanlive.boards.application.port.out.BoardRepository;
import com.kanbanlive.boards.application.port.out.WorkspaceDirectory;
import com.kanbanlive.boards.domain.model.Board;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateBoardService implements CreateBoardUseCase {

    private final BoardRepository boards;
    private final WorkspaceDirectory workspaces;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateBoardService(BoardRepository boards, WorkspaceDirectory workspaces, IdGenerator ids, Clock clock) {
        this.boards = boards;
        this.workspaces = workspaces;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public BoardSummary execute(CreateBoardCommand command) {
        if (!workspaces.exists(command.workspaceId())) {
            throw new NotFoundException("workspace " + command.workspaceId() + " not found");
        }
        Board board = Board.create(ids.newId(), command.workspaceId(), command.name(), clock.now());
        return BoardSummaries.of(boards.save(board));
    }
}
