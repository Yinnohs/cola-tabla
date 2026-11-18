package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.application.port.in.RenameBoardUseCase;
import com.kanbanlive.boards.application.port.out.BoardRepository;
import com.kanbanlive.boards.domain.model.Board;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RenameBoardService implements RenameBoardUseCase {

    private final BoardRepository boards;
    private final Clock clock;

    public RenameBoardService(BoardRepository boards, Clock clock) {
        this.boards = boards;
        this.clock = clock;
    }

    @Override
    public BoardSummary execute(RenameBoardCommand command) {
        Board board = boards.findById(command.boardId())
                .orElseThrow(() -> new NotFoundException("board " + command.boardId() + " not found"));
        if (board.version() != command.expectedVersion()) {
            throw new StaleVersionException("board " + board.id() + " is at version " + board.version()
                    + ", expected " + command.expectedVersion());
        }
        return BoardSummaries.of(boards.save(board.rename(command.name(), clock.now())));
    }
}
