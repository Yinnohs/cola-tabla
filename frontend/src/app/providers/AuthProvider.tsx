import type { ReactNode } from 'react'
import { useSession } from '../../features/auth'

/** Compuerta de sesion: no pinta la aplicacion hasta restaurar la sesion guardada en sessionStorage. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSession()
  if (session.status === 'loading') return <p role="status">Cargando sesión...</p>
  return <>{children}</>
}
