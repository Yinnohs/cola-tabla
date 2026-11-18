package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.RecordBoardEventUseCase;
import com.kanbanlive.boards.application.port.out.BoardEventLog;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class RecordBoardEventService implements RecordBoardEventUseCase {

    private final BoardEventLog log;

    RecordBoardEventService(BoardEventLog log) {
        this.log = log;
    }

    @Override
    public long execute(UUID boardId, String type, String payloadJson) {
        return log.append(boardId, type, payloadJson);
    }
}
