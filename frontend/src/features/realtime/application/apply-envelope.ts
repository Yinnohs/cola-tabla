import { applyBoardEvent, type NormalizedBoard } from '../../boards'
import { applyCardEvent } from '../../cards'
import { applyListEvent } from '../../lists'
import type { BoardEnvelope } from '../domain/board-envelope'

/**
 * Orquesta: traduce un envelope al evento de cada feature y delega en SU reducer puro
 * (`realtime` no sabe aplicar tarjetas ni listas). Devuelve `null` si el cliente no sabe aplicar el evento
 * (tipo desconocido): el llamador debe recargar el tablero.
 */
export function applyEnvelope(
  nb: NormalizedBoard,
  envelope: BoardEnvelope,
): NormalizedBoard | null {
  switch (envelope.type) {
    case 'board.updated':
      return applyBoardEvent(nb, { type: 'board.updated', board: envelope.payload.board })
    case 'list.created':
    case 'list.updated':
    case 'list.moved': {
      const { id, name, position, version } = envelope.payload.list
      return applyListEvent(nb, { type: envelope.type, list: { id, name, position, version } })
    }
    case 'card.created':
    case 'card.updated':
      return applyCardEvent(nb, { type: envelope.type, card: envelope.payload.card })
    case 'card.moved':
      return applyCardEvent(nb, {
        type: 'card.moved',
        card: envelope.payload.card,
        fromListId: envelope.payload.fromListId,
      })
    case 'card.deleted':
      return applyCardEvent(nb, {
        type: 'card.deleted',
        cardId: envelope.payload.cardId,
        listId: envelope.payload.listId,
      })
    case 'unknown':
      return null
  }
}
