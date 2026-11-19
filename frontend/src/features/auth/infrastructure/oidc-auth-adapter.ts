import {
  ANONYMOUS_SESSION,
  LOADING_SESSION,
  authenticated,
  safeReturnTo,
  type AuthSession,
} from '../domain/auth-session'
import type { AuthPort } from '../application/ports/auth-port'

/** Subconjunto de `User` de oidc-client-ts que usa el adapter. */
export interface OidcUser {
  access_token: string
  expired?: boolean
  state?: unknown
  profile: { sub: string; name?: string; preferred_username?: string; email?: string }
}

/** Subconjunto de `UserManager` de oidc-client-ts (permite sustituirlo en los tests). */
export interface OidcUserManager {
  getUser(): Promise<OidcUser | null>
  signinRedirect(args?: { state?: unknown }): Promise<void>
  signinRedirectCallback(): Promise<OidcUser>
  signinSilent(): Promise<OidcUser | null>
  signoutRedirect(): Promise<void>
  events: {
    addUserLoaded(callback: (user: OidcUser) => void): void
    addUserUnloaded(callback: () => void): void
    addSilentRenewError(callback: (error: Error) => void): void
  }
}

export interface OidcAuthDeps {
  userManager: OidcUserManager
  /** Ruta actual (path + query + hash): a donde volver si la renovacion del token falla y hay que re-loguear. */
  currentPath: () => string
}

function toSession(user: OidcUser | null): AuthSession {
  if (!user) return ANONYMOUS_SESSION
  const p = user.profile
  return authenticated({
    id: p.sub,
    name: p.name ?? p.preferred_username ?? p.sub,
    email: p.email ?? null,
  })
}

/**
 * Adapter OIDC (Authorization Code + PKCE) sobre oidc-client-ts. Mantiene la sesion en memoria como
 * "store externo" (getSnapshot/subscribe) y la alimenta con los eventos del UserManager.
 */
export class OidcAuthAdapter implements AuthPort {
  private readonly um: OidcUserManager
  private readonly currentPath: () => string
  private user: OidcUser | null = null
  private session: AuthSession = LOADING_SESSION
  private readonly listeners = new Set<() => void>()
  private initialization?: Promise<void>

  constructor({ userManager, currentPath }: OidcAuthDeps) {
    this.um = userManager
    this.currentPath = currentPath
    userManager.events.addUserLoaded((user) => this.setUser(user))
    userManager.events.addUserUnloaded(() => this.setUser(null))
    // Renovacion silenciosa fallida (refresh_token caducado/revocado): re-login conservando la ruta.
    userManager.events.addSilentRenewError(() => {
      this.setUser(null)
      void this.login(this.currentPath())
    })
  }

  readonly getSnapshot = (): AuthSession => this.session

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  initialize(): Promise<void> {
    this.initialization ??= this.restore()
    return this.initialization
  }

  getToken(): string | null {
    return this.user && !this.user.expired ? this.user.access_token : null
  }

  async login(returnTo: string): Promise<void> {
    await this.um.signinRedirect({ state: { returnTo: safeReturnTo(returnTo) } })
  }

  async completeLogin(): Promise<string> {
    const user = await this.um.signinRedirectCallback()
    this.setUser(user)
    const state = user.state as { returnTo?: unknown } | undefined | null
    return safeReturnTo(state?.returnTo)
  }

  async logout(): Promise<void> {
    await this.um.signoutRedirect()
  }

  async refresh(): Promise<boolean> {
    try {
      const user = await this.um.signinSilent()
      this.setUser(user)
      return this.user !== null
    } catch {
      this.setUser(null)
      return false
    }
  }

  private async restore(): Promise<void> {
    try {
      const stored = await this.um.getUser()
      if (stored?.expired) {
        // Token caducado con la pestana cerrada o dormida: se intenta renovar con el refresh_token.
        await this.refresh()
        return
      }
      this.setUser(stored)
    } catch {
      this.setUser(null)
    }
  }

  private setUser(user: OidcUser | null) {
    this.user = user && !user.expired ? user : null
    const next = toSession(this.user)
    const changed = next.status !== this.session.status || next.user?.id !== this.session.user?.id
    if (!changed) return
    this.session = next
    this.listeners.forEach((listener) => listener())
  }
}
