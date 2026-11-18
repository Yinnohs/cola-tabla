package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.ResolveBoardWorkspaceUseCase;
import com.kanbanlive.boards.application.port.out.BoardRepository;
import com.kanbanlive.boards.domain.model.Board;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResolveBoardWorkspaceService implements ResolveBoardWorkspaceUseCase {

    private final BoardRepository repository;

    public ResolveBoardWorkspaceService(BoardRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> execute(UUID boardId) {
        return repository.findById(boardId).map(Board::workspaceId);
    }
}
