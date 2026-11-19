import { useAuthActions } from '../application/use-auth-actions'
import { useSession } from '../application/use-session'

/** Nombre de la persona autenticada y boton de cierre de sesion (nada si no hay sesion). */
export function UserMenu() {
  const session = useSession()
  const { logout } = useAuthActions()
  if (session.status !== 'authenticated') return null
  return (
    <span className="user-menu">
      <span aria-label="Usuario actual">{session.user.name}</span>
      <button type="button" className="btn-ghost" onClick={() => void logout()}>
        Cerrar sesión
      </button>
    </span>
  )
}
