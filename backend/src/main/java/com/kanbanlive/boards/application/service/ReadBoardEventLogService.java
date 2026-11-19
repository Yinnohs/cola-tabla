package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardEventRecord;
import com.kanbanlive.boards.application.port.in.ReadBoardEventLogUseCase;
import com.kanbanlive.boards.application.port.out.BoardEventLog;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ReadBoardEventLogService implements ReadBoardEventLogUseCase {

    private final BoardEventLog log;

    ReadBoardEventLogService(BoardEventLog log) {
        this.log = log;
    }

    @Override
    public long currentSeq(UUID boardId) {
        return log.currentSeq(boardId).orElseThrow(() -> new NotFoundException("board " + boardId + " not found"));
    }

    @Override
    public List<BoardEventRecord> eventsAfter(UUID boardId, long since, long upTo, int limit) {
        return log.findAfter(boardId, since, upTo, limit);
    }
}
