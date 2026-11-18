import {
  err,
  ok,
  validateName,
  type CardId,
  type FractionalIndex,
  type ListId,
  type Result,
} from '../../../shared/kernel'

export interface Card {
  readonly id: CardId
  readonly listId: ListId
  readonly title: string
  readonly description: string
  readonly position: FractionalIndex
  readonly version: number
}

export const CARD_TITLE_MAX_LENGTH = 255
export const CARD_DESCRIPTION_MAX_LENGTH = 10_000

export type CardTitleError = 'EMPTY' | 'TOO_LONG'
export type CardDescriptionError = 'TOO_LONG'

/** Titulo recortado, 1..255 caracteres (igual que el backend). */
export function cardTitle(raw: string): Result<string, CardTitleError> {
  return validateName(raw, CARD_TITLE_MAX_LENGTH)
}

export function cardDescription(raw: string): Result<string, CardDescriptionError> {
  return raw.length > CARD_DESCRIPTION_MAX_LENGTH ? err('TOO_LONG') : ok(raw)
}
