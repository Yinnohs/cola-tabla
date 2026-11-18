import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from './schema'
import { toApiError } from './api-error'

export type TokenProvider = () => string | null | undefined | Promise<string | null | undefined>

export interface ApiClientOptions {
  /** Origen de la API. Vacio = mismo origen (nginx `/api` o proxy de Vite en dev). */
  baseUrl?: string
  /** Punto de inyeccion del bearer token (Keycloak llegara en Fase 2). */
  getToken?: TokenProvider
  fetch?: (request: Request) => Promise<Response>
}

export type ApiClient = ReturnType<typeof createClient<paths>>

/** Cliente tipado generado desde docs/contracts/openapi.json. Solo lo usan capas `infrastructure`. */
export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const client = createClient<paths>({
    baseUrl: options.baseUrl ?? '',
    // fetch resuelto en cada llamada (no al crear el cliente): permite que MSW lo parchee en tests.
    fetch: options.fetch ?? ((request: Request) => globalThis.fetch(request)),
  })
  const auth: Middleware = {
    async onRequest({ request }) {
      const token = await options.getToken?.()
      if (token) request.headers.set('Authorization', `Bearer ${token}`)
      return request
    },
  }
  client.use(auth)
  return client
}

/** Resultado de una llamada openapi-fetch. */
interface FetchResult<T> {
  data?: T
  error?: unknown
  response: Response
}

/** Devuelve `data` o lanza `ApiError` (ProblemDetail parseado). */
export async function unwrap<T>(call: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await call
  if (!response.ok) throw toApiError(response, error)
  return data as T
}

/** Base URL por defecto: `VITE_API_BASE_URL` o mismo origen (`/api` via nginx / proxy Vite). */
export function defaultApiBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''
}
