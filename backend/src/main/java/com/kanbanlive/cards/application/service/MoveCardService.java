package com.kanbanlive.cards.application.service;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.cards.domain.model.Card;
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
public class MoveCardService implements MoveCardUseCase {

    private final CardRepository cards;
    private final ListDirectory lists;
    private final Clock clock;
    private final CardEvents events;

    public MoveCardService(CardRepository cards, ListDirectory lists, Clock clock,
            BoardChangePublisher publisher) {
        this.events = new CardEvents(lists, publisher);
        this.cards = cards;
        this.lists = lists;
        this.clock = clock;
    }

    @Override
    public CardSummary execute(MoveCardCommand command) {
        Card card = cards.findById(command.cardId())
                .orElseThrow(() -> new NotFoundException("card " + command.cardId() + " not found"));
        if (!lists.exists(command.targetListId())) {
            throw new NotFoundException("list " + command.targetListId() + " not found");
        }
        UUID fromListId = card.listId();
        FractionalIndex position;
        try {
            position = allocate(card, command);
        } catch (RebalanceRequiredException exhausted) {
            // Rebalanceo en la misma transaccion: reescribe las hermanas y reintenta con posiciones frescas.
            cards.updatePositions(Positions.rebalance(cards.findIdsInOrder(command.targetListId())), clock.now());
            card = cards.findById(command.cardId())
                    .orElseThrow(() -> new NotFoundException("card " + command.cardId() + " not found"));
            position = allocate(card, command);
        }
        CardSummary saved = CardSummaries.of(cards.saveMove(card.moveTo(command.targetListId(), position, clock.now())));
        events.moved(saved, fromListId);
        return saved;
    }

    private FractionalIndex allocate(Card card, MoveCardCommand command) {
        FractionalIndex prev = neighbour(card, command.targetListId(), command.prevCardId());
        FractionalIndex next = neighbour(card, command.targetListId(), command.nextCardId());
        return Positions.allocate(prev, next);
    }

    private FractionalIndex neighbour(Card card, UUID targetListId, UUID neighbourId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(card.id())) {
            throw new ValidationException("a card cannot be its own neighbour");
        }
        return cards.findPositionInList(targetListId, neighbourId)
                .orElseThrow(() -> new NotFoundException(
                        "card " + neighbourId + " not found in list " + targetListId));
    }
}
