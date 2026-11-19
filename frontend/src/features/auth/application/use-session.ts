import { useSyncExternalStore } from 'react'
import { usePorts } from '../../../app/di'
import type { AuthSession } from '../domain/auth-session'

/** Sesion actual, reactiva a los cambios del puerto de autenticacion. */
export function useSession(): AuthSession {
  const { auth } = usePorts()
  return useSyncExternalStore(auth.subscribe, auth.getSnapshot)
}
