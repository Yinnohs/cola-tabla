import { describe, expect, it } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { FakeAuthPort, SESSION_LOADING, SIGNED_OUT, renderWithPorts } from '../../../shared/testing'
import { AuthCallback } from './AuthCallback'
import { ProtectedRoute } from './ProtectedRoute'
import { SignedOut } from './SignedOut'
import { UserMenu } from './UserMenu'

function renderApp(auth: FakeAuthPort, path: string) {
  return renderWithPorts(
    <MemoryRouter initialEntries={[path]}>
      <UserMenu />
      <Routes>
        <Route path="/auth/callback" element={<AuthCallback />} />
        <Route path="/auth/signed-out" element={<SignedOut />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/secreto" element={<p>Contenido protegido</p>} />
          <Route path="/boards/:id" element={<p>Tablero protegido</p>} />
        </Route>
        <Route path="/destino" element={<p>Destino tras login</p>} />
      </Routes>
    </MemoryRouter>,
    { ports: { auth } },
  )
}

describe('ProtectedRoute', () => {
  it('con sesion muestra el contenido', () => {
    renderApp(new FakeAuthPort(), '/secreto')
    expect(screen.getByText('Contenido protegido')).toBeInTheDocument()
  })

  it('sin sesion redirige al login conservando la ruta pedida (path, query y hash)', async () => {
    const auth = new FakeAuthPort(SIGNED_OUT)
    renderApp(auth, '/boards/7?vista=1#c')
    expect(screen.getByRole('status')).toHaveTextContent('Redirigiendo')
    await waitFor(() => expect(auth.loginCalls).toEqual(['/boards/7?vista=1#c']))
    expect(screen.queryByText('Tablero protegido')).not.toBeInTheDocument()
  })

  it('mientras se restaura la sesion no redirige ni muestra contenido', () => {
    const auth = new FakeAuthPort(SESSION_LOADING)
    renderApp(auth, '/secreto')
    expect(screen.getByRole('status')).toHaveTextContent('Comprobando sesión')
    expect(auth.loginCalls).toEqual([])
  })

  it('reacciona al cambio de sesion del puerto (useSyncExternalStore)', async () => {
    const auth = new FakeAuthPort(SESSION_LOADING)
    renderApp(auth, '/secreto')
    act(() =>
      auth.setSession({ status: 'authenticated', user: { id: 'u', name: 'U', email: null } }),
    )
    expect(await screen.findByText('Contenido protegido')).toBeInTheDocument()
  })

  it('si falla la redireccion ofrece un boton para reintentar', async () => {
    const auth = new FakeAuthPort(SIGNED_OUT)
    auth.loginFailure = new Error('sin red')
    renderApp(auth, '/secreto')
    expect(await screen.findByRole('alert')).toHaveTextContent('sin red')
    auth.loginFailure = undefined
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(auth.loginCalls).toEqual(['/secreto', '/secreto'])
  })
})

describe('AuthCallback', () => {
  it('canjea el codigo y navega a la ruta guardada', async () => {
    const auth = new FakeAuthPort(SIGNED_OUT)
    auth.callbackResult = '/destino'
    renderApp(auth, '/auth/callback?code=abc&state=xyz')
    expect(await screen.findByText('Destino tras login')).toBeInTheDocument()
  })

  it('si falla el canje muestra el error y permite reintentar', async () => {
    const auth = new FakeAuthPort(SIGNED_OUT)
    auth.callbackResult = new Error('state mismatch')
    renderApp(auth, '/auth/callback')
    expect(await screen.findByRole('alert')).toHaveTextContent('state mismatch')
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar inicio de sesión' }))
    expect(auth.loginCalls).toEqual(['/'])
  })
})

describe('UserMenu y SignedOut', () => {
  it('muestra el nombre y cierra sesion', async () => {
    const auth = new FakeAuthPort()
    renderApp(auth, '/secreto')
    expect(screen.getByLabelText('Usuario actual')).toHaveTextContent('Ana Test')
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    expect(auth.logoutCalls).toBe(1)
  })

  it('sin sesion no hay menu de usuario', () => {
    renderApp(new FakeAuthPort(SIGNED_OUT), '/auth/signed-out')
    expect(screen.queryByRole('button', { name: 'Cerrar sesión' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sesión cerrada' })).toBeInTheDocument()
  })

  it('la pantalla de sesion cerrada permite volver a entrar', async () => {
    const auth = new FakeAuthPort(SIGNED_OUT)
    renderApp(auth, '/auth/signed-out')
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    expect(auth.loginCalls).toEqual(['/'])
  })
})
