import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPorts, createTabClientId, oidcConfigFromEnv, wsUrlFromEnv } from './create-ports'
import { isClientId } from '../../shared/kernel'

afterEach(() => vi.unstubAllEnvs())

describe('oidcConfigFromEnv', () => {
  it('usa los valores de desarrollo por defecto (Keycloak local, cliente kanban-web)', () => {
    vi.stubEnv('VITE_OIDC_AUTHORITY', '')
    vi.stubEnv('VITE_OIDC_CLIENT_ID', '')
    expect(oidcConfigFromEnv('http://localhost:5173')).toEqual({
      authority: 'http://localhost:8180/realms/kanban',
      clientId: 'kanban-web',
      redirectUri: 'http://localhost:5173/auth/callback',
      postLogoutRedirectUri: 'http://localhost:5173/auth/signed-out',
    })
  })

  it('las variables VITE_OIDC_* tienen prioridad', () => {
    vi.stubEnv('VITE_OIDC_AUTHORITY', 'https://idp.example/realms/x')
    vi.stubEnv('VITE_OIDC_CLIENT_ID', 'otro')
    vi.stubEnv('VITE_OIDC_REDIRECT_URI', 'https://app.example/auth/callback')
    vi.stubEnv('VITE_OIDC_POST_LOGOUT_REDIRECT_URI', 'https://app.example/')
    expect(oidcConfigFromEnv('http://x')).toEqual({
      authority: 'https://idp.example/realms/x',
      clientId: 'otro',
      redirectUri: 'https://app.example/auth/callback',
      postLogoutRedirectUri: 'https://app.example/',
    })
  })
})

describe('createPorts', () => {
  it('cablea todos los puertos y la sesion empieza en loading, sin token', () => {
    const ports = createPorts()
    expect(Object.keys(ports).sort()).toEqual(
      ['auth', 'boards', 'cards', 'clientId', 'events', 'lists', 'members', 'workspaces'].sort(),
    )
    expect(ports.auth.getSnapshot().status).toBe('loading')
    expect(ports.auth.getToken()).toBeNull()
  })

  it('cada llamada genera un clientId valido y distinto; events expone subscribe/fetchEventsSince', () => {
    const a = createPorts()
    const b = createPorts()
    expect(isClientId(a.clientId)).toBe(true)
    expect(a.clientId).not.toBe(b.clientId)
    expect(typeof a.events.subscribe).toBe('function')
    expect(typeof a.events.fetchEventsSince).toBe('function')
  })
})

describe('createTabClientId', () => {
  it('cumple el formato del contrato', () => {
    expect(isClientId(createTabClientId())).toBe(true)
  })
})

describe('wsUrlFromEnv', () => {
  it('mismo origen: ws:// para http y wss:// para https', () => {
    vi.stubEnv('VITE_WS_URL', '')
    expect(wsUrlFromEnv({ protocol: 'http:', host: 'localhost:5173' })).toBe(
      'ws://localhost:5173/ws',
    )
    expect(wsUrlFromEnv({ protocol: 'https:', host: 'app.example' })).toBe('wss://app.example/ws')
  })

  it('VITE_WS_URL tiene prioridad', () => {
    vi.stubEnv('VITE_WS_URL', 'ws://otro:9000/ws')
    expect(wsUrlFromEnv({ protocol: 'http:', host: 'x' })).toBe('ws://otro:9000/ws')
  })
})
