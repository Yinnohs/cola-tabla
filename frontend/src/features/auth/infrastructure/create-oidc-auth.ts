import { UserManager, WebStorageStateStore } from 'oidc-client-ts'
import type { AuthPort } from '../application/ports/auth-port'
import { OidcAuthAdapter, type OidcUserManager } from './oidc-auth-adapter'

export interface OidcConfig {
  authority: string
  clientId: string
  redirectUri: string
  postLogoutRedirectUri: string
}

/** Scopes: `offline_access` hace que Keycloak entregue refresh_token (renovacion sin iframe ni redireccion). */
export const OIDC_SCOPE = 'openid profile offline_access'

export function createOidcAuth(config: OidcConfig): AuthPort {
  const userManager = new UserManager({
    authority: config.authority,
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    post_logout_redirect_uri: config.postLogoutRedirectUri,
    response_type: 'code', // Authorization Code + PKCE (S256 en oidc-client-ts)
    scope: OIDC_SCOPE,
    automaticSilentRenew: true,
    // Tokens solo en sessionStorage (ADR-0009): no sobreviven al cierre de la pestana.
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  })
  return new OidcAuthAdapter({
    userManager: userManager as unknown as OidcUserManager,
    currentPath: () =>
      `${window.location.pathname}${window.location.search}${window.location.hash}`,
  })
}
