package com.kanbanlive.lists.application.service;

import com.kanbanlive.lists.application.port.in.CheckListExistsUseCase;
import com.kanbanlive.lists.application.port.out.ListRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckListExistsService implements CheckListExistsUseCase {

    private final ListRepository repository;

    public CheckListExistsService(ListRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean execute(UUID listId) {
        return repository.existsById(listId);
    }
}
