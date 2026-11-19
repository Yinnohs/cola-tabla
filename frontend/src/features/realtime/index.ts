// API publica de la feature realtime (unico punto de import desde fuera)
export type {
  BoardEnvelope,
  BoardEnvelopeMeta,
  BoardEvent,
  BoardEventType,
  BoardPayload,
  CardPayload,
  ListPayload,
} from './domain/board-envelope'
export { SUPPORTED_SCHEMA_VERSION } from './domain/board-envelope'
export type { ConnectionStatus } from './domain/connection-status'
export { decideSequence, type SequenceDecision } from './domain/sequence-tracker'
export type {
  BoardEventsPage,
  BoardEventsPort,
  Unsubscribe,
} from './application/ports/board-events-port'
export { useBoardSync } from './application/use-board-sync'
export type { BoardSyncSnapshot } from './application/board-sync-session'
