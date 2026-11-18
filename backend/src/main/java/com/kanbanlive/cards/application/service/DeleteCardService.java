package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.DeleteCardUseCase;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;

import com.kanbanlive.sharedkernel.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteCardService implements DeleteCardUseCase {

    private final CardRepository cards;

    private final CardEvents events;

    public DeleteCardService(CardRepository cards, ListDirectory lists, BoardChangePublisher publisher) {
        this.cards = cards;
        this.events = new CardEvents(lists, publisher);
    }

    @Override
    public void execute(DeleteCardCommand command) {
        Card card = cards.findById(command.cardId())
                .orElseThrow(() -> new NotFoundException("card " + command.cardId() + " not found"));
        cards.deleteById(command.cardId());
        events.deleted(card.id(), card.listId());
    }
}
