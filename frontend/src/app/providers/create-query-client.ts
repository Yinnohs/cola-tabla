import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import type { AuthPort } from '../../features/auth'
import { AppError } from '../../shared/lib'
import type { AccessNotices } from './access-notices'
import { createAccessErrorHandler } from './handle-access-errors'

export interface QueryClientDeps {
  auth: Pick<AuthPort, 'refresh' | 'login'>
  notices: Pick<AccessNotices, 'reportForbidden'>
  currentPath?: () => string
}

/** QueryClient de la aplicacion: reintentos solo ante 5xx y gestion global de 401/403 (F2.5). */
export function createAppQueryClient({ auth, notices, currentPath }: QueryClientDeps): QueryClient {
  const handle = createAccessErrorHandler({
    auth,
    notices,
    onSessionRenewed: () => void client.invalidateQueries(),
    currentPath:
      currentPath ??
      (() => `${window.location.pathname}${window.location.search}${window.location.hash}`),
  })
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: handle }),
    mutationCache: new MutationCache({ onError: handle }),
    defaultOptions: {
      queries: {
        // Un 4xx (404, 401, 403) no se arregla reintentando.
        retry: (count, error) => !(error instanceof AppError && error.status < 500) && count < 3,
      },
    },
  })
  return client
}
