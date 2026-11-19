import { reconcileCard, removeCard, type BoardCardView, type NormalizedBoard } from '../../boards'
import type { CardId, ListId } from '../../../shared/kernel'

/** Eventos de tarjeta ya traducidos al dominio (el adapter de `realtime` hace la traduccion del JSON). */
export type CardEvent =
  | { readonly type: 'card.created'; readonly card: BoardCardView }
  | { readonly type: 'card.updated'; readonly card: BoardCardView }
  | { readonly type: 'card.moved'; readonly card: BoardCardView; readonly fromListId: ListId }
  | { readonly type: 'card.deleted'; readonly cardId: CardId; readonly listId: ListId }

/**
 * Reducer puro `(NormalizedBoard, CardEvent) -> NormalizedBoard`.
 * - created/updated/moved: upsert guardado por `version` (un evento duplicado o anterior no retrocede el estado;
 *   `moved` reubica la tarjeta aunque cambie de lista porque `upsertCard` la saca de la lista origen).
 * - deleted: idempotente.
 * Vive en `application` (no en `domain`) porque usa los helpers de cache de `boards`, y el dominio de una
 * feature no puede importar otra feature (dependency-cruiser `domain-is-pure`).
 */
export function applyCardEvent(nb: NormalizedBoard, event: CardEvent): NormalizedBoard {
  switch (event.type) {
    case 'card.created':
    case 'card.updated':
    case 'card.moved':
      return reconcileCard(nb, event.card)
    case 'card.deleted':
      return removeCard(nb, event.cardId)
  }
}
