package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.DeleteCardUseCase;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteCardService implements DeleteCardUseCase {

    private final CardRepository cards;

    public DeleteCardService(CardRepository cards) {
        this.cards = cards;
    }

    @Override
    public void execute(DeleteCardCommand command) {
        if (cards.findById(command.cardId()).isEmpty()) {
            throw new NotFoundException("card " + command.cardId() + " not found");
        }
        cards.deleteById(command.cardId());
    }
}
