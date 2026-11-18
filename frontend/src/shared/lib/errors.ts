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
