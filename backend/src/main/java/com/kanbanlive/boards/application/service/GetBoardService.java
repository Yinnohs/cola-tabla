package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import com.kanbanlive.boards.application.port.in.GetBoardUseCase;
import com.kanbanlive.boards.application.port.out.BoardDetailsReader;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
// REPEATABLE_READ: las tres consultas del reader ven una unica instantanea (estado + lastSeq coherentes).
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
