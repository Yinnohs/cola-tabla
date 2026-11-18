/**
 * Port TypeScript de `FractionalIndex` del backend (ADR-0003).
 * Posicion ordenable estilo LexoRank: fraccion 0.d1d2d3... en base 62 como string.
 * Alfabeto en orden ASCII: el orden natural de strings (`<`) coincide con COLLATE "C" de Postgres.
 * Debe pasar los mismos vectores que el backend (docs/contracts/fractional-index-vectors.json).
 */

export const ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
export const BASE = ALPHABET.length
/** Longitud a partir de la cual conviene rebalancear la lista. */
export const EXHAUSTION_LENGTH = 32
export const MAX_LENGTH = 64

/** Posicion valida (no vacia, solo alfabeto, sin '0' final). */
export type FractionalIndex = string & { readonly __brand: 'FractionalIndex' }

export class FractionalIndexError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'FractionalIndexError'
  }
}

/** Devuelve el motivo de invalidez o null si es valida. */
function validationProblem(value: string): string | null {
  if (typeof value !== 'string' || value.length === 0) return 'La posicion no puede estar vacia'
  if (value.length > MAX_LENGTH) return `La posicion supera ${MAX_LENGTH} caracteres`
  for (const ch of value) {
    if (ALPHABET.indexOf(ch) < 0) return `Caracter invalido en la posicion: '${ch}'`
  }
  if (value.endsWith(ALPHABET.charAt(0))) return "La posicion no puede terminar en '0'"
  return null
}

export function isValidFractionalIndex(value: string): value is FractionalIndex {
  return validationProblem(value) === null
}

export function fractionalIndex(value: string): FractionalIndex {
  const problem = validationProblem(value)
  if (problem) throw new FractionalIndexError(problem)
  return value as FractionalIndex
}

/** Comparacion por code units (equivale a COLLATE "C" en ASCII). */
export function compareFractionalIndex(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0
}

export function isExhausted(value: string): boolean {
  return value.length >= EXHAUSTION_LENGTH
}

function digitAt(s: string, i: number): number {
  return i < s.length ? ALPHABET.indexOf(s.charAt(i)) : 0
}

/** a: fraccion inferior ("" = 0); b: fraccion superior o null (= 1). Precondicion a < b. */
function midpoint(a: string, b: string | null): string {
  if (b !== null) {
    let n = 0
    while (n < b.length && digitAt(a, n) === digitAt(b, n)) n++
    if (n > 0) {
      return b.slice(0, n) + midpoint(a.length > n ? a.slice(n) : '', b.slice(n))
    }
  }
  const digitA = digitAt(a, 0)
  const digitB = b === null ? BASE : ALPHABET.indexOf(b.charAt(0))
  if (digitB - digitA > 1) {
    return ALPHABET.charAt(Math.floor((digitA + digitB + 1) / 2))
  }
  if (b !== null && b.length > 1) return b.slice(0, 1)
  return ALPHABET.charAt(digitA) + midpoint(a.length > 1 ? a.slice(1) : '', null)
}

/**
 * Posicion estrictamente entre `prev` y `next`. `null`/`undefined` = sin limite
 * (prev null inserta al principio, next null al final, ambos null en lista vacia).
 */
export function between(
  prev: string | null | undefined,
  next: string | null | undefined,
): FractionalIndex {
  const p = prev ?? null
  const n = next ?? null
  if (p !== null) fractionalIndex(p)
  if (n !== null) fractionalIndex(n)
  if (p !== null && n !== null && compareFractionalIndex(p, n) >= 0) {
    throw new FractionalIndexError('prev debe ser estrictamente menor que next')
  }
  return fractionalIndex(midpoint(p ?? '', n))
}

function encode(num: bigint, digits: number): string {
  const chars: string[] = new Array<string>(digits)
  const base = BigInt(BASE)
  for (let i = digits - 1; i >= 0; i--) {
    chars[i] = ALPHABET.charAt(Number(num % base))
    num /= base
  }
  let end = digits
  while (end > 1 && chars[end - 1] === ALPHABET.charAt(0)) end--
  return chars.slice(0, end).join('')
}

/** `count` posiciones crecientes y equiespaciadas, con holgura para inserciones posteriores. */
export function evenlySpaced(count: number): FractionalIndex[] {
  if (!Number.isInteger(count) || count < 0) {
    throw new FractionalIndexError('count no puede ser negativo')
  }
  if (count === 0) return []
  const base = BigInt(BASE)
  const needed = BigInt(4 * (count + 1))
  let k = 1
  let space = base
  while (space < needed) {
    k++
    space *= base
  }
  const step = space / BigInt(count + 1)
  const out: FractionalIndex[] = []
  for (let i = 1; i <= count; i++) out.push(fractionalIndex(encode(step * BigInt(i), k)))
  return out
}
