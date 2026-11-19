/** Identificador de instancia (pestana) enviado en `X-Client-Id`: `[A-Za-z0-9._:-]{1,64}` (docs/contracts/board-events.md). */
export const CLIENT_ID_PATTERN = /^[A-Za-z0-9._:-]{1,64}$/

export function isClientId(value: unknown): value is string {
  return typeof value === 'string' && CLIENT_ID_PATTERN.test(value)
}

/**
 * Normaliza un valor aleatorio (p. ej. `crypto.randomUUID()`) al formato permitido: sustituye los caracteres
 * no validos por `-` y recorta a 64. Si no queda nada, lanza (no hay clientId util).
 */
export function toClientId(raw: string): string {
  const cleaned = raw.replace(/[^A-Za-z0-9._:-]/g, '-').slice(0, 64)
  if (cleaned === '') throw new Error('clientId vacio')
  return cleaned
}
