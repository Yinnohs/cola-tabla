/** Persona autenticada (claims del id_token). `id` es el `sub` de Keycloak: el que se usa para invitar. */
export interface AuthUser {
  readonly id: string
  readonly name: string
  readonly email: string | null
}

/** Estado de la sesion: `loading` mientras se restaura desde el almacenamiento del navegador. */
export type AuthSession =
  | { readonly status: 'loading'; readonly user: null }
  | { readonly status: 'unauthenticated'; readonly user: null }
  | { readonly status: 'authenticated'; readonly user: AuthUser }

export const LOADING_SESSION: AuthSession = { status: 'loading', user: null }
export const ANONYMOUS_SESSION: AuthSession = { status: 'unauthenticated', user: null }

export function authenticated(user: AuthUser): AuthSession {
  return { status: 'authenticated', user }
}

/** Ruta de retorno segura tras el login: solo rutas internas (evita open redirect). */
export function safeReturnTo(raw: unknown, fallback = '/'): string {
  if (typeof raw !== 'string') return fallback
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return fallback
  if (raw.startsWith('/auth/')) return fallback
  return raw
}
