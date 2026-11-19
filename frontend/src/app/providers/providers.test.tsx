import { describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClientProvider, useQuery } from '@tanstack/react-query'
import { AppError } from '../../shared/lib'
import { FakeAuthPort, SESSION_LOADING, SIGNED_OUT } from '../../shared/testing'
import { PortsProvider } from '../di'
import { fakePorts } from '../../shared/testing'
import {
  AccessNoticesContext,
  AuthProvider,
  ForbiddenBanner,
  RENEWAL_GRACE_MS,
  createAccessErrorHandler,
  createAccessNotices,
  createAppQueryClient,
} from './index'

describe('AuthProvider', () => {
  it('no pinta la app mientras se restaura la sesion y luego la muestra', () => {
    const auth = new FakeAuthPort(SESSION_LOADING)
    render(
      <PortsProvider ports={fakePorts({ auth })}>
        <AuthProvider>
          <p>App lista</p>
        </AuthProvider>
      </PortsProvider>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Cargando sesión')
    expect(screen.queryByText('App lista')).not.toBeInTheDocument()
    act(() => auth.setSession(SIGNED_OUT))
    expect(screen.getByText('App lista')).toBeInTheDocument()
  })
})

function handlerSetup(refreshResult = true) {
  const auth = new FakeAuthPort()
  auth.refreshResult = refreshResult
  const notices = createAccessNotices()
  const onSessionRenewed = vi.fn()
  let clock = 1_000_000
  const handle = createAccessErrorHandler({
    auth,
    notices,
    onSessionRenewed,
    currentPath: () => '/boards/5?x=1',
    now: () => clock,
  })
  return { auth, notices, onSessionRenewed, handle, tick: (ms: number) => void (clock += ms) }
}

const unauthorized = () => new AppError('token', 401, 'HTTP_401')
const forbidden = () => new AppError('no', 403, 'FORBIDDEN')

describe('createAccessErrorHandler (F2.5)', () => {
  it('401: renueva el token y reintenta las consultas', async () => {
    const s = handlerSetup(true)
    s.handle(unauthorized())
    await waitFor(() => expect(s.onSessionRenewed).toHaveBeenCalledTimes(1))
    expect(s.auth.refreshCalls).toBe(1)
    expect(s.auth.loginCalls).toEqual([])
  })

  it('401 con la renovacion fallida: login guardando la ruta actual', async () => {
    const s = handlerSetup(false)
    s.handle(unauthorized())
    await waitFor(() => expect(s.auth.loginCalls).toEqual(['/boards/5?x=1']))
    expect(s.onSessionRenewed).not.toHaveBeenCalled()
  })

  it('varios 401 simultaneos provocan una sola renovacion', async () => {
    const s = handlerSetup(true)
    s.handle(unauthorized())
    s.handle(unauthorized())
    s.handle(unauthorized())
    await waitFor(() => expect(s.onSessionRenewed).toHaveBeenCalledTimes(1))
    expect(s.auth.refreshCalls).toBe(1)
  })

  it('un 401 justo despues de renovar (el servidor sigue rechazando) va directo a login', async () => {
    const s = handlerSetup(true)
    s.handle(unauthorized())
    await waitFor(() => expect(s.onSessionRenewed).toHaveBeenCalled())
    s.tick(RENEWAL_GRACE_MS - 1)
    s.handle(unauthorized())
    await waitFor(() => expect(s.auth.loginCalls).toEqual(['/boards/5?x=1']))
    expect(s.auth.refreshCalls).toBe(1)
  })

  it('403: avisa de falta de permiso, sin tocar la sesion', () => {
    const s = handlerSetup()
    s.handle(forbidden())
    expect(s.notices.getSnapshot()).toBe(true)
    expect(s.auth.refreshCalls).toBe(0)
    expect(s.auth.loginCalls).toEqual([])
  })

  it('otros errores se ignoran', () => {
    const s = handlerSetup()
    s.handle(new AppError('x', 404, 'NOT_FOUND'))
    s.handle(new Error('red'))
    expect(s.notices.getSnapshot()).toBe(false)
    expect(s.auth.refreshCalls).toBe(0)
  })
})

describe('createAppQueryClient + ForbiddenBanner', () => {
  function Probe({ status }: { status: number }) {
    const q = useQuery({
      queryKey: ['probe', status],
      queryFn: () => Promise.reject(new AppError('rechazado', status, `HTTP_${status}`)),
    })
    return <p>{q.isError ? 'fallo' : 'cargando'}</p>
  }

  it('un 403 en una consulta muestra el aviso global y se puede cerrar', async () => {
    const notices = createAccessNotices()
    const client = createAppQueryClient({ auth: new FakeAuthPort(), notices })
    render(
      <QueryClientProvider client={client}>
        <AccessNoticesContext.Provider value={notices}>
          <ForbiddenBanner />
          <Probe status={403} />
        </AccessNoticesContext.Provider>
      </QueryClientProvider>,
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso')
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar aviso' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('un 401 en una consulta con renovacion imposible redirige al login', async () => {
    const auth = new FakeAuthPort()
    auth.refreshResult = false
    const client = createAppQueryClient({
      auth,
      notices: createAccessNotices(),
      currentPath: () => '/workspaces/1',
    })
    render(
      <QueryClientProvider client={client}>
        <Probe status={401} />
      </QueryClientProvider>,
    )
    await waitFor(() => expect(auth.loginCalls).toEqual(['/workspaces/1']))
  })

  it('un 4xx no se reintenta', async () => {
    const client = createAppQueryClient({
      auth: new FakeAuthPort(),
      notices: createAccessNotices(),
    })
    const retry = client.getDefaultOptions().queries?.retry as (c: number, e: unknown) => boolean
    expect(retry(0, new AppError('x', 404, 'NOT_FOUND'))).toBe(false)
    expect(retry(0, new AppError('x', 503, 'HTTP_503'))).toBe(true)
    expect(retry(3, new AppError('x', 503, 'HTTP_503'))).toBe(false)
  })
})
