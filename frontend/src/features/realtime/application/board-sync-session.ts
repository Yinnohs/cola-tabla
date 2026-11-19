import type { QueryClient } from '@tanstack/react-query'
import { boardKeys, withLastSeq, type NormalizedBoard } from '../../boards'
import type { BoardId } from '../../../shared/kernel'
import type { BoardEnvelope } from '../domain/board-envelope'
import type { ConnectionStatus } from '../domain/connection-status'
import { decideSequence } from '../domain/sequence-tracker'
import { applyEnvelope } from './apply-envelope'
import type { BoardEventsPort } from './ports/board-events-port'

export interface BoardSyncSnapshot {
  readonly connection: ConnectionStatus
}

export interface BoardSyncDeps {
  readonly boardId: BoardId
  readonly events: BoardEventsPort
  readonly queryClient: QueryClient
  /** `clientId` de esta pestana: sus propios eventos no se reaplican (pero si avanzan `lastSeq`). */
  readonly clientId: string
}

/** Recargas consecutivas maximas ante una carga que queda por detras de lo visto en el WS. */
const MAX_RESYNC_ROUNDS = 5

/**
 * Store externo (compatible con `useSyncExternalStore`) que sincroniza UN tablero con el tiempo real.
 * La suscripcion al puerto vive mientras haya al menos un listener: `subscribe` (de useSyncExternalStore)
 * la abre y su baja la cierra, asi no hace falta `useEffect` (ADR-0004, ADR-0010).
 *
 * Reglas:
 * - El WS nunca es fuente de verdad: la cache (`boardKeys.detail`) lleva `lastSeq`; ante hueco, tipo desconocido
 *   o cualquier duda se recarga el tablero por REST.
 * - Eventos propios (`clientId`): no se reaplican, pero avanzan `lastSeq`.
 * - Mientras hay una recarga en curso los eventos solo anotan el `seq` maximo visto; al terminar, si la carga
 *   quedo por detras de lo visto, se recarga otra vez (converge sin reproducir eventos).
 * - Un vigilante de la cache detecta recargas externas (foco, invalidaciones, rollback) que dejan `lastSeq`
 *   por detras de lo visto y las corrige.
 * - Al conectar se consulta el log (`fetchEventsSince`) por si algo ocurrio entre la carga y la suscripcion.
 */
export function createBoardSyncSession(deps: BoardSyncDeps) {
  const { boardId, events, queryClient, clientId } = deps
  const key = boardKeys.detail(boardId)
  const listeners = new Set<() => void>()
  let snapshot: BoardSyncSnapshot = { connection: 'connecting' }
  let stopEvents: (() => void) | null = null
  let stopCache: (() => void) | null = null
  let maxSeen = 0
  let resyncing = false
  let catchUpController: AbortController | null = null

  const read = () => queryClient.getQueryData<NormalizedBoard>(key)

  function setConnection(connection: ConnectionStatus) {
    if (snapshot.connection === connection) return
    snapshot = { connection }
    listeners.forEach((l) => l())
  }

  /** Recarga el tablero completo y repite mientras la carga quede por detras de lo visto en el WS. */
  async function resync(): Promise<void> {
    if (resyncing) return
    resyncing = true
    try {
      for (let round = 0; round < MAX_RESYNC_ROUNDS; round++) {
        const before = read()?.lastSeq ?? -1
        await queryClient.refetchQueries({ queryKey: key }).catch(() => undefined)
        const after = read()?.lastSeq ?? -1
        if (after >= maxSeen || after <= before) break
      }
    } finally {
      resyncing = false
    }
  }

  function handleEvent(envelope: BoardEnvelope) {
    if (envelope.boardId !== boardId) return
    maxSeen = Math.max(maxSeen, envelope.seq)
    if (resyncing) return
    const current = read()
    if (!current) return // aun no hay carga: al llegar, el vigilante compara con `maxSeen`
    const decision = decideSequence(current.lastSeq, envelope.seq)
    if (decision === 'IGNORE_DUPLICATE') return
    if (decision === 'RESYNC') {
      void resync()
      return
    }
    const own = envelope.clientId !== null && envelope.clientId === clientId
    const next = own ? current : applyEnvelope(current, envelope)
    if (next === null) {
      void resync()
      return
    }
    queryClient.setQueryData<NormalizedBoard>(key, withLastSeq(next, envelope.seq))
  }

  /** Al conectar: se perdio algo entre la carga REST y la suscripcion? (consulta barata del log). */
  async function catchUp() {
    const current = read()
    if (!current) return
    catchUpController?.abort()
    const controller = new AbortController()
    catchUpController = controller
    try {
      const page = await events.fetchEventsSince(boardId, current.lastSeq, controller.signal)
      if (controller.signal.aborted) return
      if (page.lastSeq > (read()?.lastSeq ?? Infinity)) {
        maxSeen = Math.max(maxSeen, page.lastSeq)
        void resync()
      }
    } catch {
      // 400/410/red: no sabemos si falta algo, ante duda se recarga
      if (!controller.signal.aborted) void resync()
    }
  }

  function handleStatus(status: ConnectionStatus) {
    setConnection(status)
    if (status === 'connected') void catchUp()
  }

  function start() {
    stopEvents = events.subscribe(boardId, handleEvent, handleStatus)
    stopCache = queryClient.getQueryCache().subscribe((e) => {
      if (e.type !== 'updated' || e.action.type !== 'success') return
      if (JSON.stringify(e.query.queryKey) !== JSON.stringify(key)) return
      const data = e.query.state.data as NormalizedBoard | undefined
      if (data && data.lastSeq < maxSeen && !resyncing) void resync()
    })
  }

  function stop() {
    stopEvents?.()
    stopCache?.()
    catchUpController?.abort()
    stopEvents = stopCache = null
    catchUpController = null
    maxSeen = 0
    snapshot = { connection: 'connecting' }
  }

  return {
    subscribe(listener: () => void): () => void {
      listeners.add(listener)
      if (listeners.size === 1) start()
      return () => {
        listeners.delete(listener)
        if (listeners.size === 0) stop()
      }
    },
    getSnapshot: (): BoardSyncSnapshot => snapshot,
  }
}

export type BoardSyncSession = ReturnType<typeof createBoardSyncSession>
