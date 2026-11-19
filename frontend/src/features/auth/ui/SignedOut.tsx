import { useAuthActions } from '../application/use-auth-actions'

/** Pantalla publica tras cerrar sesion (post_logout_redirect_uri). */
export function SignedOut() {
  const { login } = useAuthActions()
  return (
    <section>
      <h1>Sesión cerrada</h1>
      <p>Has cerrado sesión correctamente.</p>
      <button type="button" onClick={() => void login('/')}>
        Iniciar sesión
      </button>
    </section>
  )
}
