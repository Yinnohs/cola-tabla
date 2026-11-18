package com.kanbanlive.sharedkernel.position;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Unico punto por el que los casos de uso obtienen una posicion nueva. Centraliza la deteccion de espacio agotado
 * para que el rebalanceo (B1.7) se enganche en un solo sitio.
 */
public final class Positions {

    private Positions() {
    }

    /**
     * Posicion estrictamente entre {@code prev} y {@code next} ({@code null} = sin limite).
     *
     * @throws RebalanceRequiredException si la clave resultante esta agotada y hay que rebalancear
     */
    public static FractionalIndex allocate(FractionalIndex prev, FractionalIndex next) {
        FractionalIndex position = FractionalIndex.between(prev, next);
        if (position.isExhausted()) {
            throw new RebalanceRequiredException("position space exhausted between " + prev + " and " + next);
        }
        return position;
    }

    /**
     * Asigna posiciones equiespaciadas ({@link FractionalIndex#evenlySpaced(int)}) a los ids dados, que deben venir
     * ya en el orden actual. Es la parte pura del rebalanceo; persistirlo es cosa del puerto de salida.
     */
    public static List<PositionAssignment> rebalance(List<UUID> idsInOrder) {
        List<FractionalIndex> spaced = FractionalIndex.evenlySpaced(idsInOrder.size());
        return IntStream.range(0, idsInOrder.size())
                .mapToObj(i -> new PositionAssignment(idsInOrder.get(i), spaced.get(i)))
                .toList();
    }
}
