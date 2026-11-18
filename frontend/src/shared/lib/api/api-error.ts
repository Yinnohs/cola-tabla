import { AppError, StaleVersionError, type ProblemDetail } from '../errors'

/** Error tipado de la API: lleva `status`, `code` estable (STALE_VERSION, ...) y el ProblemDetail. */
export type ApiError = AppError

function asProblem(body: unknown): ProblemDetail | undefined {
  return body && typeof body === 'object' ? (body as ProblemDetail) : undefined
}

/** RFC 7807 ProblemDetail -> ApiError (409/STALE_VERSION -> StaleVersionError). */
export function toApiError(response: Response, body: unknown): ApiError {
  const problem = asProblem(body)
  const text = typeof body === 'string' && body !== '' ? body : undefined
  const message =
    problem?.detail ?? problem?.title ?? text ?? (response.statusText || `HTTP ${response.status}`)
  const code = problem?.code
  if (response.status === 409 && (code === undefined || code === 'STALE_VERSION')) {
    return new StaleVersionError(message, problem)
  }
  return new AppError(message, response.status, code ?? `HTTP_${response.status}`, problem)
}
