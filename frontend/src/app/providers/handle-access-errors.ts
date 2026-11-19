import { isForbidden, isUnauthorized } from '../../shared/lib'
import type { AuthPort } from '../../features/auth'
import type { AccessNotices } from './access-notices'

export interface AccessErrorDeps {
  auth: Pick<AuthPort, 'refresh' | 'login'>
  notices: Pick<AccessNotices, 'reportForbidden'>
  /** Tras renovar el token: vuelve a pedir lo que fallo con 401. */
  onSessionRenewed: () => void
  /** Ruta actual, para volver a ella tras el re-login. */
  currentPath: () => string
  now?: () => number
}

/** Si tras renovar el token el servidor sigue dando 401 en este margen, no se reintenta: re-login. */
export const RENEWAL_GRACE_MS = 10_000

/**
 * Gestor global de errores de acceso (se engancha a QueryCache/MutationCache, sin acoplar shared a features):
 * 401 => intenta renovar el token y, si no puede, redirige al login guardando la ruta; 403 => aviso de sin permiso.
 */
export function createAccessErrorHandler(deps: AccessErrorDeps): (error: unknown) => void {
  const now = deps.now ?? Date.now
  let inFlight: Promise<void> | undefined
  let lastRenewalAt = Number.NEGATIVE_INFINITY

  async function recover(): Promise<void> {
    const renewedRecently = now() - lastRenewalAt < RENEWAL_GRACE_MS
    const renewed = renewedRecently ? false : await deps.auth.refresh()
    if (renewed) {
      lastRenewalAt = now()
      deps.onSessionRenewed()
      return
    }
    await deps.auth.login(deps.currentPath())
  }

  return (error) => {
    if (isUnauthorized(error)) {
      // Varias peticiones fallan a la vez con el mismo 401: una sola recuperacion.
      inFlight ??= recover()
        .catch(() => undefined)
        .finally(() => {
          inFlight = undefined
        })
      return
    }
    if (isForbidden(error)) deps.notices.reportForbidden()
  }
}
