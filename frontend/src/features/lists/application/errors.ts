import type { ListNameError } from '../domain/list'

export class InvalidListNameError extends Error {
  readonly reason: ListNameError
  constructor(reason: ListNameError) {
    super(reason === 'EMPTY' ? 'El nombre es obligatorio' : 'El nombre es demasiado largo')
    this.name = 'InvalidListNameError'
    this.reason = reason
  }
}
