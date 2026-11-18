package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
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
public class CreateCardService implements CreateCardUseCase {

    private final CardRepository cards;
    private final ListDirectory lists;
    private final IdGenerator ids;
    private final Clock clock;
    private final CardEvents events;

    public CreateCardService(CardRepository cards, ListDirectory lists, IdGenerator ids, Clock clock,
            BoardChangePublisher publisher) {
        this.events = new CardEvents(lists, publisher);
        this.cards = cards;
        this.lists = lists;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public CardSummary execute(CreateCardCommand command) {
        if (!lists.exists(command.listId())) {
            throw new NotFoundException("list " + command.listId() + " not found");
        }
        FractionalIndex position;
        try {
            position = appendPosition(command.listId());
        } catch (RebalanceRequiredException exhausted) {
            cards.updatePositions(Positions.rebalance(cards.findIdsInOrder(command.listId())), clock.now());
            position = appendPosition(command.listId());
        }
        Card card = Card.create(ids.newId(), command.listId(), command.title(), command.description(), position,
                clock.now());
        CardSummary saved = CardSummaries.of(cards.save(card));
        events.created(saved);
        return saved;
    }

    private FractionalIndex appendPosition(UUID listId) {
        return Positions.allocate(cards.findLastPosition(listId).orElse(null), null);
    }
}
