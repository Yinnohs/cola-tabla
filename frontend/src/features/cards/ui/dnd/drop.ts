import type { Active, Over } from '@dnd-kit/core'
import type { CardId, ListId } from '../../../../shared/kernel'
import type { NormalizedBoard } from '../../../boards'
import { resolveCardDrop, type CardDrop, type CardDropOver } from '../../domain/card-ordering'

export type DragKind = 'card' | 'list'

export function kindOf(item: Active | Over | null | undefined): DragKind | null {
  const type = item?.data.current?.type
  return type === 'card' || type === 'list' ? type : null
}

/** true si el centro de lo arrastrado esta por debajo del centro del elemento sobre el que esta. */
function isBelow(active: Active, over: Over): boolean {
  const moving = active.rect.current.translated
  if (!moving) return false
  return moving.top + moving.height / 2 > over.rect.top + over.rect.height / 2
}

/** Traduce el `over` de dnd-kit a un destino de dominio para tarjetas. */
export function cardOverOf(active: Active, over: Over | null): CardDropOver | null {
  const kind = kindOf(over)
  if (!over || !kind) return null
  return kind === 'card'
    ? { kind: 'card', cardId: over.id as CardId, below: isBelow(active, over) }
    : { kind: 'list', listId: over.id as ListId }
}

export function cardIdsByList(board: NormalizedBoard): Record<ListId, readonly CardId[]> {
  const out: Record<ListId, readonly CardId[]> = {}
  for (const id of board.listIds) out[id] = board.lists[id]!.cardIds
  return out
}

/** Destino de una tarjeta arrastrada, o null si no hay cambio. */
export function resolveCardDragTarget(
  board: NormalizedBoard,
  active: Active,
  over: Over | null,
): CardDrop | null {
  const target = cardOverOf(active, over)
  return target ? resolveCardDrop(cardIdsByList(board), active.id as CardId, target) : null
}

/** Indice destino de una lista arrastrada sobre otra lista (semantica arrayMove). */
export function resolveListDragIndex(
  board: NormalizedBoard,
  active: Active,
  over: Over | null,
): number | null {
  if (!over || kindOf(over) !== 'list' || over.id === active.id) return null
  const index = board.listIds.indexOf(over.id as ListId)
  return index < 0 ? null : index
}
