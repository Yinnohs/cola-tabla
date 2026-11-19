/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origen de la API; vacio = mismo origen. */
  readonly VITE_API_BASE_URL?: string
  /** Emisor OIDC (realm de Keycloak). */
  readonly VITE_OIDC_AUTHORITY?: string
  /** Cliente publico de la SPA (PKCE). */
  readonly VITE_OIDC_CLIENT_ID?: string
  /** Por defecto: origen + /auth/callback. */
  readonly VITE_OIDC_REDIRECT_URI?: string
  /** Por defecto: origen + /auth/signed-out. */
  readonly VITE_OIDC_POST_LOGOUT_REDIRECT_URI?: string
}
