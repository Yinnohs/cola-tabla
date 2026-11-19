import type { Board } from './board'
import { withBoard, type NormalizedBoard } from './normalized-board'

/** Evento de tablero ya traducido al dominio (lo construye `realtime`; boards no conoce el WS). */
export interface BoardUpdatedEvent {
  readonly type: 'board.updated'
  readonly board: Board
}

/**
 * Reducer puro `(NormalizedBoard, BoardUpdatedEvent) -> NormalizedBoard`. Guarda por `version`: un evento
 * duplicado o anterior al estado actual no lo modifica; el de otro tablero se ignora.
 */
export function applyBoardEvent(nb: NormalizedBoard, event: BoardUpdatedEvent): NormalizedBoard {
  if (event.board.id !== nb.board.id) return nb
  if (event.board.version <= nb.board.version) return nb
  return withBoard(nb, event.board)
}
