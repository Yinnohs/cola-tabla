package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.UpdateCardUseCase;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UpdateCardService implements UpdateCardUseCase {

    private final CardRepository cards;
    private final Clock clock;

    public UpdateCardService(CardRepository cards, Clock clock) {
        this.cards = cards;
        this.clock = clock;
    }

    @Override
    public CardSummary execute(UpdateCardCommand command) {
        Card card = cards.findById(command.cardId())
                .orElseThrow(() -> new NotFoundException("card " + command.cardId() + " not found"));
        if (card.version() != command.expectedVersion()) {
            throw new StaleVersionException("card " + card.id() + " is at version " + card.version()
                    + ", expected " + command.expectedVersion());
        }
        return CardSummaries.of(cards.save(card.update(command.title(), command.description(), clock.now())));
    }
}
