package com.kanbanlive.lists.application.service;

import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.in.RenameListUseCase;
import com.kanbanlive.lists.application.port.out.ListRepository;
import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RenameListService implements RenameListUseCase {

    private final ListRepository lists;
    private final Clock clock;
    private final BoardChangePublisher publisher;

    public RenameListService(ListRepository lists, Clock clock, BoardChangePublisher publisher) {
        this.publisher = publisher;
        this.lists = lists;
        this.clock = clock;
    }

    @Override
    public ListSummary execute(RenameListCommand command) {
        BoardList list = lists.findById(command.listId())
                .orElseThrow(() -> new NotFoundException("list " + command.listId() + " not found"));
        if (list.version() != command.expectedVersion()) {
            throw new StaleVersionException("list " + list.id() + " is at version " + list.version()
                    + ", expected " + command.expectedVersion(), ListSummaries.of(list));
        }
        ListSummary saved = ListSummaries.of(lists.save(list.rename(command.name(), clock.now())));
        publisher.publish(saved.boardId(), "list.updated", java.util.Map.of("list", saved));
        return saved;
    }
}
