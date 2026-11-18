package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import com.kanbanlive.boards.application.port.in.GetBoardUseCase;
import com.kanbanlive.boards.application.port.out.BoardDetailsReader;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetBoardService implements GetBoardUseCase {

    private final BoardDetailsReader reader;

    public GetBoardService(BoardDetailsReader reader) {
        this.reader = reader;
    }

    @Override
    public BoardDetails execute(UUID boardId) {
        return reader.findById(boardId).orElseThrow(() -> new NotFoundException("board " + boardId + " not found"));
    }
}
