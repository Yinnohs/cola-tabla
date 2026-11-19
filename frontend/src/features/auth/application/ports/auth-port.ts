import type { AuthSession } from '../../domain/auth-session'

/**
 * Puerto de autenticacion. `subscribe` y `getSnapshot` son propiedades-flecha (ya enlazadas) para pasarlos
 * tal cual a `useSyncExternalStore`; `getSnapshot` devuelve el MISMO objeto mientras la sesion no cambie.
 */
export interface AuthPort {
  /** Estado de la sesion (compatible con `useSyncExternalStore`). */
  readonly getSnapshot: () => AuthSession
  readonly subscribe: (listener: () => void) => () => void
  /** Restaura la sesion guardada (idempotente). Se invoca una vez antes de pintar la aplicacion. */
  initialize(): Promise<void>
  /** Token de acceso vigente, o null si no hay sesion o ya caduco. */
  getToken(): string | null
  /** Redirige al proveedor de identidad guardando la ruta a la que volver. */
  login(returnTo: string): Promise<void>
  /** Procesa la vuelta del proveedor (`/auth/callback`) y devuelve la ruta guardada. */
  completeLogin(): Promise<string>
  /** Cierra la sesion en el proveedor y vuelve a la pantalla de sesion cerrada. */
  logout(): Promise<void>
  /** Renueva el token con el refresh_token. false si no se pudo (hay que volver a iniciar sesion). */
  refresh(): Promise<boolean>
}
