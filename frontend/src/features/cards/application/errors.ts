import type { CardDescriptionError, CardTitleError } from '../domain/card'

export class InvalidCardTitleError extends Error {
  readonly reason: CardTitleError
  constructor(reason: CardTitleError) {
    super(reason === 'EMPTY' ? 'El titulo es obligatorio' : 'El titulo es demasiado largo')
    this.name = 'InvalidCardTitleError'
    this.reason = reason
  }
}

export class InvalidCardDescriptionError extends Error {
  readonly reason: CardDescriptionError
  constructor(reason: CardDescriptionError) {
    super('La descripcion es demasiado larga')
    this.name = 'InvalidCardDescriptionError'
    this.reason = reason
  }
}

/**
 * La tarjeta cambio en el servidor desde que se cargo (STALE_VERSION). El tablero ya se ha
 * refrescado cuando se lanza: la UI puede ofrecer recargar los datos vigentes.
 */
export class CardConflictError extends Error {
  constructor() {
    super('Alguien ha modificado esta tarjeta mientras la editabas')
    this.name = 'CardConflictError'
  }
}
