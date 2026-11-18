import { compareFractionalIndex, type CardId, type ListId } from '../../../shared/kernel'
import type { Board } from './board'
import type { BoardCardView, BoardDetails, BoardListView } from './board-details'

/** Lista en la cache normalizada: guarda solo los ids de sus tarjetas (en orden). */
export interface NormalizedList extends Omit<BoardListView, 'cards'> {
  readonly cardIds: readonly CardId[]
}

/**
 * Forma de cache del tablero: entidades por id + ordenes. Los movimientos optimistas solo
 * reescriben `cardIds` de una o dos listas y la posicion de una tarjeta (sin tocar el resto).
 */
export interface NormalizedBoard {
  readonly board: Board
  readonly listIds: readonly ListId[]
  readonly lists: Readonly<Record<ListId, NormalizedList>>
  readonly cards: Readonly<Record<CardId, BoardCardView>>
}

const byPosition = (a: { position: string }, b: { position: string }) =>
  compareFractionalIndex(a.position, b.position)

export function normalizeBoard(details: BoardDetails): NormalizedBoard {
  const lists: Record<ListId, NormalizedList> = {}
  const cards: Record<CardId, BoardCardView> = {}
  const sortedLists = [...details.lists].sort(byPosition)
  for (const list of sortedLists) {
    const sortedCards = [...list.cards].sort(byPosition)
    for (const card of sortedCards) cards[card.id] = { ...card, listId: list.id }
    const { cards: _omit, ...rest } = list
    void _omit
    lists[list.id] = { ...rest, cardIds: sortedCards.map((c) => c.id) }
  }
  return { board: details.board, listIds: sortedLists.map((l) => l.id), lists, cards }
}

/** Selector: listas ordenadas con sus tarjetas resueltas (para la UI). */
export function selectLists(nb: NormalizedBoard): BoardListView[] {
  return nb.listIds.map((id) => {
    const list = nb.lists[id]!
    const { cardIds, ...rest } = list
    return { ...rest, cards: cardIds.map((cid) => nb.cards[cid]!) }
  })
}

export function selectCardsOf(nb: NormalizedBoard, listId: ListId): BoardCardView[] {
  return (nb.lists[listId]?.cardIds ?? []).map((cid) => nb.cards[cid]!)
}

export function withBoard(nb: NormalizedBoard, board: Board): NormalizedBoard {
  return { ...nb, board }
}

/** Datos de una lista que la cache necesita (sin tarjetas). */
export type ListEntity = Omit<NormalizedList, 'cardIds'>

function sortIds<Id extends string>(
  ids: readonly Id[],
  positionOf: (id: Id) => string | undefined,
): Id[] {
  return [...ids].sort((a, b) => compareFractionalIndex(positionOf(a) ?? '', positionOf(b) ?? ''))
}

/** Inserta o actualiza una lista (conserva sus tarjetas) y reordena `listIds` por posicion. */
export function upsertList(nb: NormalizedBoard, list: ListEntity): NormalizedBoard {
  const existing = nb.lists[list.id]
  const lists: Record<ListId, NormalizedList> = {
    ...nb.lists,
    [list.id]: { ...list, cardIds: existing?.cardIds ?? [] },
  }
  const ids = existing ? nb.listIds : [...nb.listIds, list.id]
  return { ...nb, lists, listIds: sortIds(ids, (id) => lists[id]?.position) }
}

/**
 * Inserta o actualiza una tarjeta. Si cambia de lista, la saca de la anterior; en la lista destino
 * se reordena por posicion. Solo se reescriben las (como mucho dos) listas afectadas.
 */
export function upsertCard(nb: NormalizedBoard, card: BoardCardView): NormalizedBoard {
  const target = nb.lists[card.listId]
  if (!target) return nb
  const previous = nb.cards[card.id]
  const cards: Record<CardId, BoardCardView> = { ...nb.cards, [card.id]: card }
  const lists: Record<ListId, NormalizedList> = { ...nb.lists }
  if (previous && previous.listId !== card.listId) {
    const origin = lists[previous.listId]
    if (origin) {
      lists[previous.listId] = { ...origin, cardIds: origin.cardIds.filter((id) => id !== card.id) }
    }
  }
  const current = lists[card.listId]!
  const ids = current.cardIds.includes(card.id) ? current.cardIds : [...current.cardIds, card.id]
  lists[card.listId] = { ...current, cardIds: sortIds(ids, (id) => cards[id]?.position) }
  return { ...nb, cards, lists }
}

export function removeCard(nb: NormalizedBoard, id: CardId): NormalizedBoard {
  const card = nb.cards[id]
  if (!card) return nb
  const { [id]: _removed, ...cards } = nb.cards
  void _removed
  const list = nb.lists[card.listId]
  const lists = list
    ? { ...nb.lists, [list.id]: { ...list, cardIds: list.cardIds.filter((c) => c !== id) } }
    : nb.lists
  return { ...nb, cards, lists }
}
