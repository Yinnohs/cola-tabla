import type { BoardId } from '../../../../shared/kernel'
import type { BoardEnvelope } from '../../domain/board-envelope'
import type { ConnectionStatus } from '../../domain/connection-status'

/** Respuesta de `GET /api/boards/{id}/events?since=N` (mismo formato de envelope que el WebSocket). */
export interface BoardEventsPage {
  readonly boardId: BoardId
  readonly since: number
  readonly lastSeq: number
  readonly events: readonly BoardEnvelope[]
}

export type Unsubscribe = () => void

/**
 * Puerto de eventos de tablero en tiempo real. El WebSocket solo NOTIFICA: nunca es fuente de verdad.
 * - `subscribe` no bloquea: devuelve la baja y notifica el estado por `onStatus` (primero `connecting`).
 *   Varias suscripciones al mismo tablero son independientes; la conexion se comparte.
 * - `fetchEventsSince` consulta el log REST (Fase 4 lo usara para reanudar; en Fase 3 solo detecta cambios
 *   perdidos al conectar). Rechaza con `AppError` (400 INVALID_SINCE, 410 EVENTS_GONE, ...).
 */
export interface BoardEventsPort {
  subscribe(
    boardId: BoardId,
    onEvent: (envelope: BoardEnvelope) => void,
    onStatus: (status: ConnectionStatus) => void,
  ): Unsubscribe
  fetchEventsSince(boardId: BoardId, since: number, signal?: AbortSignal): Promise<BoardEventsPage>
}
