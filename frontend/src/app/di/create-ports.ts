import { createApiClient, defaultApiBaseUrl } from '../../shared/lib/api'
import { HttpBoardRepository } from '../../features/boards/infrastructure/http-board-repository'
import { HttpCardRepository } from '../../features/cards/infrastructure/http-card-repository'
import { HttpListRepository } from '../../features/lists/infrastructure/http-list-repository'
import { HttpWorkspaceRepository } from '../../features/workspaces/infrastructure/http-workspace-repository'
import type { Ports } from './ports-context'

/**
 * Token solo para desarrollo manual (Fase 1, sin Keycloak en la SPA): `VITE_DEV_TOKEN` en `.env.local`.
 * `import.meta.env.DEV` es false en `vite build`, asi que el bundle de produccion nunca lo incluye.
 */
export function devToken(): string | null {
  if (!import.meta.env.DEV) return null
  const token = import.meta.env.VITE_DEV_TOKEN
  return token ? token : null
}

/** Composition root: aquí (y solo aquí) se instancian los adapters concretos. */
export function createPorts(): Ports {
  // Fase 2 sustituye este stub por el token de Keycloak.
  const auth: Ports['auth'] = { getToken: devToken }
  const api = createApiClient({ baseUrl: defaultApiBaseUrl(), getToken: () => auth.getToken() })
  return {
    auth,
    workspaces: new HttpWorkspaceRepository(api),
    boards: new HttpBoardRepository(api),
    lists: new HttpListRepository(api),
    cards: new HttpCardRepository(api),
  }
}
