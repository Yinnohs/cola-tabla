import { beforeEach, describe, expect, it, vi } from 'vitest'

interface FakeConfig {
  brokerURL: string
  reconnectDelay: number
  beforeConnect: (c: FakeClient) => void
  onConnect: () => void
  onStompError: (frame: { headers: Record<string, string>; body: string }) => void
  onWebSocketClose: () => void
}

interface Sub {
  unsubscribe(): void
}

const instances: FakeClient[] = []

class FakeClient {
  readonly config: FakeConfig
  connectHeaders: Record<string, string> = {}
  activate = vi.fn()
  deactivate = vi.fn(() => Promise.resolve())
  unsubscribed = 0
  subscribe = vi.fn<(destination: string, cb: (m: { body: string }) => void) => Sub>(() => ({
    unsubscribe: () => {
      this.unsubscribed += 1
    },
  }))
  constructor(config: FakeConfig) {
    this.config = config
    instances.push(this)
  }
}

vi.mock('@stomp/stompjs', () => ({ Client: FakeClient }))

const { createStompTransport } = await import('./stomp-transport')

function handlers() {
  return { onConnect: vi.fn(), onClose: vi.fn(), onStompError: vi.fn() }
}

beforeEach(() => {
  instances.length = 0
})

describe('createStompTransport', () => {
  it('configura brokerURL y desactiva la reconexion automatica (Fase 4)', () => {
    createStompTransport({ brokerURL: 'ws://x/ws', getToken: () => null })(handlers())
    expect(instances[0]!.config.brokerURL).toBe('ws://x/ws')
    expect(instances[0]!.config.reconnectDelay).toBe(0)
  })

  it('pone el JWT en la cabecera STOMP Authorization de cada CONNECT', () => {
    let token: string | null = 'jwt-1'
    createStompTransport({ brokerURL: 'ws://x/ws', getToken: () => token })(handlers())
    const client = instances[0]!
    client.config.beforeConnect(client)
    expect(client.connectHeaders).toEqual({ Authorization: 'Bearer jwt-1' })
    token = null
    client.config.beforeConnect(client)
    expect(client.connectHeaders).toEqual({})
  })

  it('traduce onConnect, onWebSocketClose y el mensaje del frame ERROR', () => {
    const h = handlers()
    createStompTransport({ brokerURL: 'ws://x/ws', getToken: () => null })(h)
    const { config } = instances[0]!
    config.onConnect()
    config.onWebSocketClose()
    config.onStompError({ headers: { message: 'Access denied' }, body: '' })
    config.onStompError({ headers: {}, body: 'detalle' })
    expect(h.onConnect).toHaveBeenCalled()
    expect(h.onClose).toHaveBeenCalled()
    expect(h.onStompError.mock.calls).toEqual([['Access denied'], ['detalle']])
  })

  it('activate/deactivate/subscribe delegan en el cliente y la baja es segura', () => {
    const t = createStompTransport({ brokerURL: 'ws://x/ws', getToken: () => null })(handlers())
    const client = instances[0]!
    t.activate()
    expect(client.activate).toHaveBeenCalled()
    const got: string[] = []
    const off = t.subscribe('/topic/boards.b', (body) => got.push(body))
    client.subscribe.mock.calls[0]![1]({ body: '{"a":1}' })
    expect(got).toEqual(['{"a":1}'])
    off()
    expect(client.unsubscribed).toBe(1)
    t.deactivate()
    expect(client.deactivate).toHaveBeenCalled()
  })
})
