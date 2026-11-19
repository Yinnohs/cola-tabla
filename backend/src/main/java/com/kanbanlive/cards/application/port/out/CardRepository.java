package com.kanbanlive.cards.application.port.out;

import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.PositionAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository {

    Card save(Card card);

    /**
     * Persiste un movimiento (lista + posicion) con last-write-wins: UPDATE directo de una fila SIN comprobar la
     * version cargada y con version+1 (ADR-0005). Nunca lanza StaleVersionException por concurrencia.
     */
    Card saveMove(Card card);

    Optional<Card> findById(UUID id);

    /** Borrado fisico. */
    void deleteById(UUID id);

    /** Posicion de la ultima tarjeta de la lista, o vacio si esta vacia. */
    Optional<FractionalIndex> findLastPosition(UUID listId);

    /** Posicion de la tarjeta cardId solo si esta en la lista listId. */
    Optional<FractionalIndex> findPositionInList(UUID listId, UUID cardId);

    /** Ids de las tarjetas de list en orden de posicion (para rebalancear). */
    List<UUID> findIdsInOrder(UUID listId);

    /** Reescribe las posiciones indicadas (rebalanceo): cada fila incrementa su version. Dentro de la transaccion actual. */
    void updatePositions(List<PositionAssignment> assignments, Instant now);
}
