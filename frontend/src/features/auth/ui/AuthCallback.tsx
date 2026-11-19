import { Navigate } from 'react-router'
import { safeReturnTo } from '../domain/auth-session'
import { useAuthActions } from '../application/use-auth-actions'
import { useCompleteLogin } from '../application/use-complete-login'

/** Destino de la redireccion del proveedor (`/auth/callback`): canjea el codigo y vuelve a la ruta guardada. */
export function AuthCallback() {
  const result = useCompleteLogin()
  const { login } = useAuthActions()
  if (result.isPending) return <p role="status">Completando inicio de sesión...</p>
  if (result.isError) {
    return (
      <section>
        <p role="alert">No se pudo completar el inicio de sesión: {result.error.message}</p>
        <button type="button" onClick={() => void login('/')}>
          Reintentar inicio de sesión
        </button>
      </section>
    )
  }
  return <Navigate to={safeReturnTo(result.data)} replace />
}
