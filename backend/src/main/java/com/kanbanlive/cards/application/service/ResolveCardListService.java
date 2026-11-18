package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.ResolveCardListUseCase;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.domain.model.Card;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResolveCardListService implements ResolveCardListUseCase {

    private final CardRepository repository;

    public ResolveCardListService(CardRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> execute(UUID cardId) {
        return repository.findById(cardId).map(Card::listId);
    }
}
