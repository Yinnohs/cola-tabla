import { toClientId } from '../../shared/kernel'
import { createApiClient, defaultApiBaseUrl } from '../../shared/lib/api'
import {
  createOidcAuth,
  type OidcConfig,
} from '../../features/auth/infrastructure/create-oidc-auth'
import { HttpBoardRepository } from '../../features/boards/infrastructure/http-board-repository'
import { HttpCardRepository } from '../../features/cards/infrastructure/http-card-repository'
import { HttpListRepository } from '../../features/lists/infrastructure/http-list-repository'
import { HttpBoardEventLog } from '../../features/realtime/infrastructure/http-board-event-log'
import { StompBoardEvents } from '../../features/realtime/infrastructure/stomp-board-events'
import { createStompTransport } from '../../features/realtime/infrastructure/stomp-transport'
import { HttpMemberRepository } from '../../features/workspaces/infrastructure/http-member-repository'
import { HttpWorkspaceRepository } from '../../features/workspaces/infrastructure/http-workspace-repository'
import type { Ports } from './ports-context'

/** Configuracion OIDC desde `VITE_OIDC_*` (valores por defecto = Keycloak de desarrollo, ver .env.example). */
export function oidcConfigFromEnv(origin: string = window.location.origin): OidcConfig {
  const env = import.meta.env
  return {
    authority: env.VITE_OIDC_AUTHORITY || 'http://localhost:8180/realms/kanban',
    clientId: env.VITE_OIDC_CLIENT_ID || 'kanban-web',
    redirectUri: env.VITE_OIDC_REDIRECT_URI || `${origin}/auth/callback`,
    postLogoutRedirectUri: env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI || `${origin}/auth/signed-out`,
  }
}

/** URL del endpoint STOMP: `VITE_WS_URL` o `/ws` en el mismo origen (nginx / proxy de Vite). */
export function wsUrlFromEnv(
  location: Pick<Location, 'protocol' | 'host'> = window.location,
): string {
  const configured = import.meta.env.VITE_WS_URL as string | undefined
  if (configured) return configured
  return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`
}

/** Identificador unico por pestana (en memoria; una recarga genera otro). */
export function createTabClientId(): string {
  const random =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
  return toClientId(random)
}

/** Composition root: aqui (y solo aqui) se instancian los adapters concretos. */
export function createPorts(oidc: OidcConfig = oidcConfigFromEnv()): Ports {
  const auth = createOidcAuth(oidc)
  const clientId = createTabClientId()
  const api = createApiClient({
    baseUrl: defaultApiBaseUrl(),
    getToken: () => auth.getToken(),
    clientId,
  })
  return {
    auth,
    workspaces: new HttpWorkspaceRepository(api),
    members: new HttpMemberRepository(api),
    boards: new HttpBoardRepository(api),
    lists: new HttpListRepository(api),
    cards: new HttpCardRepository(api),
    events: new StompBoardEvents({
      createTransport: createStompTransport({
        brokerURL: wsUrlFromEnv(),
        getToken: () => auth.getToken(),
      }),
      eventLog: new HttpBoardEventLog(api),
    }),
    clientId,
  }
}
