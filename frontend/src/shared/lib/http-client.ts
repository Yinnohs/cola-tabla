import { AppError, StaleVersionError, type ProblemDetail } from './errors'

export interface HttpClientOptions {
  baseUrl?: string
  getToken?: () => string | null | undefined | Promise<string | null | undefined>
  fetchImpl?: typeof fetch
}

export interface RequestOptions {
  headers?: Record<string, string>
  signal?: AbortSignal
  query?: Record<string, string | number | boolean | undefined>
}

export interface HttpClient {
  get<T>(path: string, opts?: RequestOptions): Promise<T>
  post<T>(path: string, body?: unknown, opts?: RequestOptions): Promise<T>
  put<T>(path: string, body?: unknown, opts?: RequestOptions): Promise<T>
  patch<T>(path: string, body?: unknown, opts?: RequestOptions): Promise<T>
  delete<T = void>(path: string, opts?: RequestOptions): Promise<T>
}

async function toError(res: Response): Promise<AppError> {
  let problem: ProblemDetail | undefined
  try {
    const parsed: unknown = await res.json()
    if (parsed && typeof parsed === 'object') problem = parsed as ProblemDetail
  } catch {
    problem = undefined
  }
  const message = problem?.detail ?? problem?.title ?? (res.statusText || `HTTP ${res.status}`)
  const code = problem?.code
  if (res.status === 409 && (code === undefined || code === 'STALE_VERSION')) {
    return new StaleVersionError(message, problem)
  }
  return new AppError(message, res.status, code ?? `HTTP_${res.status}`, problem)
}

/** Único wrapper de fetch de la aplicación. */
export function createHttpClient(options: HttpClientOptions = {}): HttpClient {
  const { baseUrl = '', getToken } = options

  async function request<T>(
    method: string,
    path: string,
    body: unknown,
    opts: RequestOptions = {},
  ): Promise<T> {
    const doFetch = options.fetchImpl ?? fetch
    const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers }
    const token = await getToken?.()
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    let url = `${baseUrl}${path}`
    if (opts.query) {
      const qs = new URLSearchParams()
      for (const [k, v] of Object.entries(opts.query)) if (v !== undefined) qs.set(k, String(v))
      const s = qs.toString()
      if (s) url += (url.includes('?') ? '&' : '?') + s
    }

    const res = await doFetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: opts.signal,
    })
    if (!res.ok) throw await toError(res)
    if (res.status === 204) return undefined as T
    const text = await res.text()
    return (text ? JSON.parse(text) : undefined) as T
  }

  return {
    get: (p, o) => request('GET', p, undefined, o),
    post: (p, b, o) => request('POST', p, b, o),
    put: (p, b, o) => request('PUT', p, b, o),
    patch: (p, b, o) => request('PATCH', p, b, o),
    delete: (p, o) => request('DELETE', p, undefined, o),
  }
}
