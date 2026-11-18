package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.CheckBoardExistsUseCase;
import com.kanbanlive.boards.application.port.out.BoardRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckBoardExistsService implements CheckBoardExistsUseCase {

    private final BoardRepository repository;

    public CheckBoardExistsService(BoardRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean execute(UUID boardId) {
        return repository.existsById(boardId);
    }
}
