import { Outlet, useLocation } from 'react-router'
import { useAuthActions } from '../application/use-auth-actions'
import { useLoginRedirect } from '../application/use-login-redirect'
import { useSession } from '../application/use-session'

/** Guarda de rutas (layout route): sin sesion redirige al login recordando la ruta pedida. */
export function ProtectedRoute() {
  const session = useSession()
  if (session.status === 'loading') return <p role="status">Comprobando sesión...</p>
  if (session.status === 'unauthenticated') return <LoginRedirect />
  return <Outlet />
}

function LoginRedirect() {
  const location = useLocation()
  const returnTo = `${location.pathname}${location.search}${location.hash}`
  const redirect = useLoginRedirect(returnTo)
  const { login } = useAuthActions()
  return (
    <section>
      <p role="status">Redirigiendo al inicio de sesión...</p>
      {redirect.isError && (
        <>
          <p role="alert">No se pudo iniciar la redirección: {redirect.error.message}</p>
          <button type="button" onClick={() => void login(returnTo)}>
            Iniciar sesión
          </button>
        </>
      )}
    </section>
  )
}
