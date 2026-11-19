export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  [key: string]: unknown
}

export class AppError extends Error {
  readonly status: number
  readonly code: string
  readonly problem?: ProblemDetail

  constructor(message: string, status: number, code: string, problem?: ProblemDetail) {
    super(message)
    this.name = 'AppError'
    this.status = status
    this.code = code
    this.problem = problem
  }
}

export class StaleVersionError extends AppError {
  constructor(message: string, problem?: ProblemDetail) {
    super(message, 409, 'STALE_VERSION', problem)
    this.name = 'StaleVersionError'
  }
}

/** 401: sin sesion valida (token ausente, caducado o rechazado). */
export function isUnauthorized(error: unknown): error is AppError {
  return error instanceof AppError && error.status === 401
}

/** 403: sesion valida pero sin permiso sobre el recurso. */
export function isForbidden(error: unknown): error is AppError {
  return error instanceof AppError && error.status === 403
}
