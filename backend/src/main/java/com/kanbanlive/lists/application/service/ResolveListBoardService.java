package com.kanbanlive.lists.application.service;

import com.kanbanlive.lists.application.port.in.ResolveListBoardUseCase;
import com.kanbanlive.lists.application.port.out.ListRepository;
import com.kanbanlive.lists.domain.model.BoardList;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResolveListBoardService implements ResolveListBoardUseCase {

    private final ListRepository repository;

    public ResolveListBoardService(ListRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> execute(UUID listId) {
        return repository.findById(listId).map(BoardList::boardId);
    }
}
