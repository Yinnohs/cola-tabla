import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OidcAuthAdapter, type OidcUser, type OidcUserManager } from './oidc-auth-adapter'

function aUser(overrides: Partial<OidcUser> = {}): OidcUser {
  return {
    access_token: 'access-1',
    expired: false,
    profile: { sub: 'sub-1', name: 'Olivia Owner', email: 'owner@kanban.local' },
    ...overrides,
  }
}

/** Mock de UserManager: guarda los callbacks de eventos para dispararlos desde el test. */
function mockUserManager() {
  const handlers: {
    loaded?: (u: OidcUser) => void
    unloaded?: () => void
    renewError?: (e: Error) => void
  } = {}
  const um = {
    getUser: vi.fn<OidcUserManager['getUser']>().mockResolvedValue(null),
    signinRedirect: vi.fn<OidcUserManager['signinRedirect']>().mockResolvedValue(undefined),
    signinRedirectCallback: vi.fn<OidcUserManager['signinRedirectCallback']>(),
    signinSilent: vi.fn<OidcUserManager['signinSilent']>(),
    signoutRedirect: vi.fn<OidcUserManager['signoutRedirect']>().mockResolvedValue(undefined),
    events: {
      addUserLoaded: (cb: (u: OidcUser) => void) => void (handlers.loaded = cb),
      addUserUnloaded: (cb: () => void) => void (handlers.unloaded = cb),
      addSilentRenewError: (cb: (e: Error) => void) => void (handlers.renewError = cb),
    },
  }
  return { um, handlers }
}

describe('OidcAuthAdapter', () => {
  let m: ReturnType<typeof mockUserManager>
  let adapter: OidcAuthAdapter
  let path: string

  beforeEach(() => {
    m = mockUserManager()
    path = '/boards/42?x=1'
    adapter = new OidcAuthAdapter({ userManager: m.um, currentPath: () => path })
  })

  it('empieza en loading y sin token', () => {
    expect(adapter.getSnapshot().status).toBe('loading')
    expect(adapter.getToken()).toBeNull()
  })

  it('initialize restaura la sesion guardada y avisa a los suscriptores', async () => {
    m.um.getUser.mockResolvedValue(aUser())
    const listener = vi.fn()
    adapter.subscribe(listener)
    await adapter.initialize()
    expect(adapter.getSnapshot()).toEqual({
      status: 'authenticated',
      user: { id: 'sub-1', name: 'Olivia Owner', email: 'owner@kanban.local' },
    })
    expect(adapter.getToken()).toBe('access-1')
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('initialize es idempotente y sin sesion queda unauthenticated', async () => {
    await adapter.initialize()
    await adapter.initialize()
    expect(m.um.getUser).toHaveBeenCalledTimes(1)
    expect(adapter.getSnapshot().status).toBe('unauthenticated')
  })

  it('el snapshot es estable mientras la sesion no cambia', async () => {
    m.um.getUser.mockResolvedValue(aUser())
    await adapter.initialize()
    const first = adapter.getSnapshot()
    m.handlers.loaded?.(aUser({ access_token: 'access-2' }))
    expect(adapter.getSnapshot()).toBe(first)
    expect(adapter.getToken()).toBe('access-2')
  })

  it('un token caducado guardado se renueva con signinSilent (refresh_token)', async () => {
    m.um.getUser.mockResolvedValue(aUser({ expired: true }))
    m.um.signinSilent.mockResolvedValue(aUser({ access_token: 'renovado' }))
    await adapter.initialize()
    expect(m.um.signinSilent).toHaveBeenCalledTimes(1)
    expect(adapter.getToken()).toBe('renovado')
    expect(adapter.getSnapshot().status).toBe('authenticated')
  })

  it('si la renovacion inicial falla queda sin sesion', async () => {
    m.um.getUser.mockResolvedValue(aUser({ expired: true }))
    m.um.signinSilent.mockRejectedValue(new Error('invalid_grant'))
    await adapter.initialize()
    expect(adapter.getSnapshot().status).toBe('unauthenticated')
  })

  it('getToken no devuelve un token que ya caduco', async () => {
    const user = aUser()
    m.um.getUser.mockResolvedValue(user)
    await adapter.initialize()
    user.expired = true
    expect(adapter.getToken()).toBeNull()
  })

  it('login redirige guardando la ruta de retorno (solo rutas internas)', async () => {
    await adapter.login('/workspaces/7')
    expect(m.um.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/workspaces/7' } })
    await adapter.login('https://evil.test')
    expect(m.um.signinRedirect).toHaveBeenLastCalledWith({ state: { returnTo: '/' } })
  })

  it('completeLogin guarda el usuario y devuelve la ruta guardada', async () => {
    m.um.signinRedirectCallback.mockResolvedValue(aUser({ state: { returnTo: '/boards/9' } }))
    expect(await adapter.completeLogin()).toBe('/boards/9')
    expect(adapter.getSnapshot().status).toBe('authenticated')
    expect(adapter.getToken()).toBe('access-1')
  })

  it('completeLogin sin estado vuelve a la raiz y propaga el error del proveedor', async () => {
    m.um.signinRedirectCallback.mockResolvedValueOnce(aUser())
    expect(await adapter.completeLogin()).toBe('/')
    m.um.signinRedirectCallback.mockRejectedValueOnce(new Error('state mismatch'))
    await expect(adapter.completeLogin()).rejects.toThrow('state mismatch')
  })

  it('logout delega en signoutRedirect', async () => {
    await adapter.logout()
    expect(m.um.signoutRedirect).toHaveBeenCalledTimes(1)
  })

  it('refresh devuelve true y actualiza el token; false si falla', async () => {
    m.um.signinSilent.mockResolvedValueOnce(aUser({ access_token: 'nuevo' }))
    expect(await adapter.refresh()).toBe(true)
    expect(adapter.getToken()).toBe('nuevo')

    m.um.signinSilent.mockRejectedValueOnce(new Error('boom'))
    expect(await adapter.refresh()).toBe(false)
    expect(adapter.getSnapshot().status).toBe('unauthenticated')
    expect(adapter.getToken()).toBeNull()
  })

  it('error de renovacion silenciosa (F2.4): cierra la sesion local y re-login en la ruta actual', () => {
    m.handlers.loaded?.(aUser())
    m.handlers.renewError?.(new Error('refresh token expired'))
    expect(adapter.getSnapshot().status).toBe('unauthenticated')
    expect(m.um.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/boards/42?x=1' } })
  })

  it('userUnloaded (logout en otra pestana) deja la sesion en unauthenticated', () => {
    m.handlers.loaded?.(aUser())
    m.handlers.unloaded?.()
    expect(adapter.getSnapshot().status).toBe('unauthenticated')
  })

  it('unsubscribe deja de avisar', () => {
    const listener = vi.fn()
    const off = adapter.subscribe(listener)
    off()
    m.handlers.loaded?.(aUser())
    expect(listener).not.toHaveBeenCalled()
  })
})
