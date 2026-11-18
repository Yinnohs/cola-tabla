import {
  planMove,
  type CardId,
  type FractionalIndex,
  type ListId,
  type Positioned,
} from '../../../shared/kernel'

export interface CardMovePlan {
  readonly cardId: CardId
  readonly toListId: ListId
  /** Vecinos en la lista destino: lo unico que se envia al servidor (el calcula la posicion). */
  readonly prevCardId: CardId | null
  readonly nextCardId: CardId | null
  /** Posicion local optimista (`FractionalIndex.between`) para ordenar la UI al instante. */
  readonly position: FractionalIndex
  /** El destino es el sitio actual de la tarjeta: no hay nada que hacer. */
  readonly unchanged: boolean
}

/**
 * Plan para soltar `cardId` en `dropIndex` de la lista `toListId`.
 * `targetCards` son las tarjetas ordenadas de la lista destino (con o sin la propia tarjeta);
 * `dropIndex` se interpreta sobre la lista destino SIN la tarjeta movida. Nunca reindexa.
 */
export function planCardMove(
  targetCards: readonly Positioned<CardId>[],
  cardId: CardId,
  toListId: ListId,
  dropIndex: number,
): CardMovePlan {
  const plan = planMove(targetCards, cardId, dropIndex)
  return {
    cardId,
    toListId,
    prevCardId: plan.prevId,
    nextCardId: plan.nextId,
    position: plan.position,
    unchanged: plan.unchanged,
  }
}

export type CardDropOver =
  | { readonly kind: 'card'; readonly cardId: CardId; readonly below?: boolean }
  | { readonly kind: 'list'; readonly listId: ListId }

export interface CardDrop {
  readonly toListId: ListId
  /** Indice de destino sobre la lista destino sin la tarjeta movida. */
  readonly index: number
}

/**
 * Traduce "solte la tarjeta sobre X" a (lista destino, indice). En la misma lista se comporta como
 * `arrayMove` (bajar = queda despues de X, subir = antes); entre listas se inserta antes de X, o
 * despues si `below`. Soltar sobre una lista (contenedor) la deja al final. `null` = sin cambios.
 */
export function resolveCardDrop(
  cardIdsByList: Readonly<Record<ListId, readonly CardId[]>>,
  activeId: CardId,
  over: CardDropOver,
): CardDrop | null {
  const listOf = (id: CardId) =>
    (Object.keys(cardIdsByList) as ListId[]).find((l) => cardIdsByList[l]?.includes(id))
  const origin = listOf(activeId)
  if (!origin) return null
  if (over.kind === 'list') {
    const ids = cardIdsByList[over.listId]
    if (!ids) return null
    return { toListId: over.listId, index: ids.filter((id) => id !== activeId).length }
  }
  if (over.cardId === activeId) return null
  const overList = listOf(over.cardId)
  if (!overList) return null
  const overIndex = cardIdsByList[overList]!.indexOf(over.cardId)
  if (overList === origin) return { toListId: origin, index: overIndex }
  return { toListId: overList, index: overIndex + (over.below ? 1 : 0) }
}
