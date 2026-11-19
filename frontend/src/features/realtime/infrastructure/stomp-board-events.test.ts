import { describe, expect, it, vi } from 'vitest'
import { boardId } from '../../../shared/kernel'
import type { ConnectionStatus } from '../domain/connection-status'
import { StompBoardEvents } from './stomp-board-events'
import type { StompTransport, StompTransportHandlers } from './stomp-transport'

const B1 = boardId('11111111-1111-4111-8111-111111111111')
const B2 = boardId('22222222-2222-4222-8222-222222222222')

/** Transporte de mentira: el test dispara connect/close/error y entrega frames. */
class MockTransport implements StompTransport {
  activated = 0
  deactivated = 0
  readonly destinations = new Map<string, (body: string) => void>()
  readonly unsubscribed: string[] = []
  handlers!: StompTransportHandlers

  activate() {
    this.activated += 1
  }
  deactivate() {
    this.deactivated += 1
  }
  subscribe(destination: string, onMessage: (body: string) => void) {
    this.destinations.set(destination, onMessage)
    return () => {
      this.unsubscribed.push(destination)
      this.destinations.delete(destination)
    }
  }
  deliver(destination: string, body: unknown) {
    this.destinations.get(destination)?.(typeof body === 'string' ? body : JSON.stringify(body))
  }
}

function setup() {
  const transports: MockTransport[] = []
  const log = vi.fn()
  const fetchEventsSince = vi.fn()
  const adapter = new StompBoardEvents({
    createTransport: (handlers) => {
      const t = new MockTransport()
      t.handlers = handlers
      transports.push(t)
      return t
    },
    eventLog: { fetchEventsSince },
    log,
  })
  return { adapter, transports, log, fetchEventsSince }
}

const envelope = (seq: number, id = B1) => ({
  boardId: id,
  seq,
  type: 'card.deleted',
  payload: { cardId: 'c1', listId: 'l1' },
  clientId: null,
  occurredAt: 'x',
  schemaVersion: 1,
})

describe('StompBoardEvents', () => {
  it('abre UNA conexion al primer subscribe y se suscribe a /topic/boards.{id} al conectar', () => {
    const { adapter, transports } = setup()
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B1,
      () => undefined,
      (s) => statuses.push(s),
    )
    adapter.subscribe(
      B2,
      () => undefined,
      () => undefined,
    )

    expect(transports).toHaveLength(1)
    expect(transports[0]!.activated).toBe(1)
    expect(statuses).toEqual(['connecting'])
    expect([...transports[0]!.destinations.keys()]).toEqual([])

    transports[0]!.handlers.onConnect()
    expect([...transports[0]!.destinations.keys()].sort()).toEqual([
      `/topic/boards.${B1}`,
      `/topic/boards.${B2}`,
    ])
    expect(statuses).toEqual(['connecting', 'connected'])
  })

  it('un subscribe con la conexion ya abierta se suscribe al instante y reporta connected', () => {
    const { adapter, transports } = setup()
    adapter.subscribe(
      B1,
      () => undefined,
      () => undefined,
    )
    transports[0]!.handlers.onConnect()
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B2,
      () => undefined,
      (s) => statuses.push(s),
    )
    expect(statuses).toEqual(['connected'])
    expect(transports[0]!.destinations.has(`/topic/boards.${B2}`)).toBe(true)
  })

  it('parsea los frames y los entrega a los listeners del tablero', () => {
    const { adapter, transports } = setup()
    const got: number[] = []
    adapter.subscribe(
      B1,
      (e) => got.push(e.seq),
      () => undefined,
    )
    transports[0]!.handlers.onConnect()
    transports[0]!.deliver(`/topic/boards.${B1}`, envelope(7))
    expect(got).toEqual([7])
  })

  it('un frame ilegible se registra y se ignora', () => {
    const { adapter, transports, log } = setup()
    const got: number[] = []
    adapter.subscribe(
      B1,
      (e) => got.push(e.seq),
      () => undefined,
    )
    transports[0]!.handlers.onConnect()
    transports[0]!.deliver(`/topic/boards.${B1}`, 'no es json')
    expect(got).toEqual([])
    expect(log).toHaveBeenCalled()
  })

  it('al darse de baja el ultimo listener de un tablero cancela su suscripcion; al ultimo tablero cierra', () => {
    const { adapter, transports } = setup()
    const off1 = adapter.subscribe(
      B1,
      () => undefined,
      () => undefined,
    )
    const off2 = adapter.subscribe(
      B2,
      () => undefined,
      () => undefined,
    )
    transports[0]!.handlers.onConnect()

    off1()
    off1() // idempotente
    expect(transports[0]!.unsubscribed).toEqual([`/topic/boards.${B1}`])
    expect(transports[0]!.deactivated).toBe(0)

    off2()
    expect(transports[0]!.deactivated).toBe(1)
  })

  it('Access denied -> access-denied, sin reconectar en bucle; el cierre posterior no lo pisa', () => {
    const { adapter, transports, log } = setup()
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B1,
      () => undefined,
      (s) => statuses.push(s),
    )
    transports[0]!.handlers.onConnect()

    transports[0]!.handlers.onStompError('Access denied')
    transports[0]!.handlers.onClose()

    expect(statuses).toEqual(['connecting', 'connected', 'access-denied'])
    expect(transports).toHaveLength(1) // nadie reabre la conexion
    expect(log.mock.calls[0]?.[0]).toBe('STOMP ERROR: Access denied')
  })

  it('otro ERROR (token invalido) -> error', () => {
    const { adapter, transports } = setup()
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B1,
      () => undefined,
      (s) => statuses.push(s),
    )
    transports[0]!.handlers.onStompError('Token expired')
    expect(statuses.at(-1)).toBe('error')
  })

  it('cierre del socket sin error previo -> disconnected', () => {
    const { adapter, transports } = setup()
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B1,
      () => undefined,
      (s) => statuses.push(s),
    )
    transports[0]!.handlers.onConnect()
    transports[0]!.handlers.onClose()
    expect(statuses.at(-1)).toBe('disconnected')
  })

  it('tras un fallo, un subscribe nuevo crea otra conexion (la muerta se descarta)', () => {
    const { adapter, transports } = setup()
    adapter.subscribe(
      B1,
      () => undefined,
      () => undefined,
    )
    transports[0]!.handlers.onStompError('Access denied')
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B2,
      () => undefined,
      (s) => statuses.push(s),
    )
    expect(transports).toHaveLength(2)
    expect(transports[0]!.deactivated).toBe(1)
    expect(statuses).toEqual(['connecting'])
  })

  it('callbacks tardios de un transporte descartado no afectan al vigente', () => {
    const { adapter, transports } = setup()
    adapter.subscribe(
      B1,
      () => undefined,
      () => undefined,
    )
    transports[0]!.handlers.onStompError('boom')
    const statuses: ConnectionStatus[] = []
    adapter.subscribe(
      B2,
      () => undefined,
      (s) => statuses.push(s),
    )
    transports[0]!.handlers.onClose() // llega tarde
    expect(statuses).toEqual(['connecting'])
  })

  it('fetchEventsSince delega en el log REST', async () => {
    const { adapter, fetchEventsSince } = setup()
    const page = { boardId: B1, since: 3, lastSeq: 3, events: [] }
    fetchEventsSince.mockResolvedValue(page)
    await expect(adapter.fetchEventsSince(B1, 3)).resolves.toBe(page)
    expect(fetchEventsSince).toHaveBeenCalledWith(B1, 3, undefined)
  })
})
