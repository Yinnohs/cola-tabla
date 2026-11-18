package com.kanbanlive.lists.application.service;

import com.kanbanlive.lists.application.port.in.CreateListUseCase;
import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.out.BoardDirectory;
import com.kanbanlive.lists.application.port.out.ListRepository;
import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.Positions;
import com.kanbanlive.sharedkernel.position.RebalanceRequiredException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateListService implements CreateListUseCase {

    private final ListRepository lists;
    private final BoardDirectory boards;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateListService(ListRepository lists, BoardDirectory boards, IdGenerator ids, Clock clock) {
        this.lists = lists;
        this.boards = boards;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public ListSummary execute(CreateListCommand command) {
        if (!boards.exists(command.boardId())) {
            throw new NotFoundException("board " + command.boardId() + " not found");
        }
        FractionalIndex position;
        try {
            position = appendPosition(command.boardId());
        } catch (RebalanceRequiredException exhausted) {
            lists.updatePositions(Positions.rebalance(lists.findIdsInOrder(command.boardId())), clock.now());
            position = appendPosition(command.boardId());
        }
        BoardList list = BoardList.create(ids.newId(), command.boardId(), command.name(), position, clock.now());
        return ListSummaries.of(lists.save(list));
    }

    private FractionalIndex appendPosition(UUID boardId) {
        return Positions.allocate(lists.findLastPosition(boardId).orElse(null), null);
    }
}
