import { err, ok, type Result } from './result'

export const NAME_MAX_LENGTH = 120

export type NameError = 'EMPTY' | 'TOO_LONG'

/** Nombre de workspace/board/lista: recortado, 1..120 caracteres (igual que el backend). */
export function validateName(raw: string, maxLength = NAME_MAX_LENGTH): Result<string, NameError> {
  const name = raw.trim()
  if (name === '') return err('EMPTY')
  if (name.length > maxLength) return err('TOO_LONG')
  return ok(name)
}
