package com.kanbanlive.lists.application.service;

import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.in.MoveListUseCase;
import com.kanbanlive.lists.application.port.out.ListRepository;
import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.Positions;
import com.kanbanlive.sharedkernel.position.RebalanceRequiredException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MoveListService implements MoveListUseCase {

    private final ListRepository lists;
    private final Clock clock;
    private final BoardChangePublisher publisher;

    public MoveListService(ListRepository lists, Clock clock, BoardChangePublisher publisher) {
        this.publisher = publisher;
        this.lists = lists;
        this.clock = clock;
    }

    @Override
    public ListSummary execute(MoveListCommand command) {
        BoardList list = lists.findById(command.listId())
                .orElseThrow(() -> new NotFoundException("list " + command.listId() + " not found"));
        FractionalIndex position;
        try {
            position = allocate(list, command);
        } catch (RebalanceRequiredException exhausted) {
            // Rebalanceo en la misma transaccion: reescribe las hermanas y reintenta con posiciones frescas.
            lists.updatePositions(Positions.rebalance(lists.findIdsInOrder(list.boardId())), clock.now());
            list = lists.findById(command.listId())
                    .orElseThrow(() -> new NotFoundException("list " + command.listId() + " not found"));
            position = allocate(list, command);
        }
        ListSummary saved = ListSummaries.of(lists.saveMove(list.moveTo(position, clock.now())));
        publisher.publish(saved.boardId(), "list.moved", java.util.Map.of("list", saved));
        return saved;
    }

    private FractionalIndex allocate(BoardList list, MoveListCommand command) {
        FractionalIndex prev = neighbour(list, command.prevListId());
        FractionalIndex next = neighbour(list, command.nextListId());
        return Positions.allocate(prev, next);
    }

    private FractionalIndex neighbour(BoardList list, UUID neighbourId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(list.id())) {
            throw new ValidationException("a list cannot be its own neighbour");
        }
        return lists.findPositionInBoard(list.boardId(), neighbourId)
                .orElseThrow(() -> new NotFoundException(
                        "list " + neighbourId + " not found in board " + list.boardId()));
    }
}
