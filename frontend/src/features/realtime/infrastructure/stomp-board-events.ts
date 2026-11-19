import type { BoardId } from '../../../shared/kernel'
import type {
  BoardEventsPage,
  BoardEventsPort,
  Unsubscribe,
} from '../application/ports/board-events-port'
import type { BoardEnvelope } from '../domain/board-envelope'
import type { ConnectionStatus } from '../domain/connection-status'
import { parseEnvelopeJson } from './parse-envelope'
import type { StompTransport, StompTransportFactory } from './stomp-transport'

interface Listener {
  readonly onEvent: (envelope: BoardEnvelope) => void
  readonly onStatus: (status: ConnectionStatus) => void
}

interface BoardSubscription {
  readonly listeners: Set<Listener>
  /** Baja STOMP de `/topic/boards.{id}`; null si aun no hay conexion abierta. */
  stompUnsubscribe: (() => void) | null
}

export interface StompBoardEventsOptions {
  createTransport: StompTransportFactory
  /** Log REST para `fetchEventsSince` (implementado por `HttpBoardEventLog`). */
  eventLog: {
    fetchEventsSince(
      boardId: BoardId,
      since: number,
      signal?: AbortSignal,
    ): Promise<BoardEventsPage>
  }
  /** Para registrar frames ERROR y mensajes ilegibles (por defecto `console.warn`). */
  log?: (message: string, detail?: unknown) => void
}

const ACCESS_DENIED = /access denied/i

/**
 * Adapter STOMP del puerto `BoardEventsPort`: UNA conexion compartida y una suscripcion STOMP por tablero
 * abierto (con tantos listeners como `subscribe` haya sobre ese tablero). La conexion se abre con el primer
 * `subscribe` y se cierra al darse de baja el ultimo. Sin reconexion automatica (Fase 4).
 * Un frame ERROR con "Access denied" produce `access-denied` y NO se reintenta.
 */
export class StompBoardEvents implements BoardEventsPort {
  private readonly options: StompBoardEventsOptions
  private readonly boards = new Map<BoardId, BoardSubscription>()
  private transport: StompTransport | null = null
  private connected = false
  /** Estado terminal de la conexion actual (error/denegado/cerrada) hasta que se cree otra. */
  private failure: ConnectionStatus | null = null

  constructor(options: StompBoardEventsOptions) {
    this.options = options
  }

  subscribe(
    boardId: BoardId,
    onEvent: (envelope: BoardEnvelope) => void,
    onStatus: (status: ConnectionStatus) => void,
  ): Unsubscribe {
    const listener: Listener = { onEvent, onStatus }
    let entry = this.boards.get(boardId)
    if (!entry) {
      entry = { listeners: new Set(), stompUnsubscribe: null }
      this.boards.set(boardId, entry)
    }
    entry.listeners.add(listener)
    this.ensureConnection()
    onStatus(this.status())
    if (this.connected) this.attach(boardId, entry)

    let active = true
    return () => {
      if (!active) return
      active = false
      this.release(boardId, listener)
    }
  }

  fetchEventsSince(
    boardId: BoardId,
    since: number,
    signal?: AbortSignal,
  ): Promise<BoardEventsPage> {
    return this.options.eventLog.fetchEventsSince(boardId, since, signal)
  }

  private status(): ConnectionStatus {
    if (this.connected) return 'connected'
    return this.failure ?? 'connecting'
  }

  private ensureConnection() {
    if (this.transport && !this.failure) return
    this.transport?.deactivate()
    this.connected = false
    this.failure = null
    // Los callbacks de un transporte descartado (deactivate asincrono) no deben afectar al vigente.
    const current = (fn: () => void) => () => {
      if (this.transport === created) fn()
    }
    const created: StompTransport = this.options.createTransport({
      onConnect: current(() => {
        this.connected = true
        for (const [boardId, entry] of this.boards) this.attach(boardId, entry)
        this.broadcast('connected')
      }),
      onStompError: (message) => {
        if (this.transport !== created) return
        this.log(`STOMP ERROR: ${message}`)
        this.fail(ACCESS_DENIED.test(message) ? 'access-denied' : 'error')
      },
      // tras un ERROR el servidor cierra el socket: se conserva el motivo (access-denied / error)
      onClose: current(() => this.fail('disconnected', false)),
    })
    this.transport = created
    this.transport.activate()
  }

  private fail(status: ConnectionStatus, overwrite = true) {
    this.connected = false
    for (const entry of this.boards.values()) entry.stompUnsubscribe = null
    if (this.failure && !overwrite) return
    this.failure = status
    this.broadcast(status)
  }

  private attach(boardId: BoardId, entry: BoardSubscription) {
    if (entry.stompUnsubscribe || !this.transport) return
    entry.stompUnsubscribe = this.transport.subscribe(`/topic/boards.${boardId}`, (body) => {
      let envelope: BoardEnvelope
      try {
        envelope = parseEnvelopeJson(body)
      } catch (error) {
        this.log('Evento de tablero ilegible; se ignora (el hueco de seq lo detectara)', error)
        return
      }
      for (const l of [...entry.listeners]) l.onEvent(envelope)
    })
  }

  private release(boardId: BoardId, listener: Listener) {
    const entry = this.boards.get(boardId)
    if (!entry) return
    entry.listeners.delete(listener)
    if (entry.listeners.size > 0) return
    entry.stompUnsubscribe?.()
    this.boards.delete(boardId)
    if (this.boards.size === 0) this.shutdown()
  }

  private shutdown() {
    this.transport?.deactivate()
    this.transport = null
    this.connected = false
    this.failure = null
  }

  private broadcast(status: ConnectionStatus) {
    for (const entry of this.boards.values()) {
      for (const l of [...entry.listeners]) l.onStatus(status)
    }
  }

  private log(message: string, detail?: unknown) {
    ;(this.options.log ?? ((m, d) => console.warn(m, d ?? '')))(message, detail)
  }
}
