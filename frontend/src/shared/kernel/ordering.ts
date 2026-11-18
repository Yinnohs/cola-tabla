import { between, type FractionalIndex } from './fractional-index'

/** Elemento ordenable por posicion fraccionaria. */
export interface Positioned<Id extends string = string> {
  readonly id: Id
  readonly position: string
}

/**
 * Resultado de soltar un elemento en un hueco: ids de los vecinos (lo que se envia al servidor, que
 * es quien calcula la posicion definitiva) y una posicion local optimista para ordenar al instante.
 */
export interface MovePlan<Id extends string> {
  readonly prevId: Id | null
  readonly nextId: Id | null
  readonly position: FractionalIndex
  /** true si soltar ahi deja el elemento donde ya estaba (no hay que llamar al servidor). */
  readonly unchanged: boolean
}

/**
 * Plan para insertar `movingId` en el indice `index` de `target` (la lista destino ya ordenada,
 * incluyendo o no al propio elemento). `index` se interpreta sobre la lista SIN el elemento movido y
 * se acota a [0, longitud]. Nunca reindexa: la posicion sale de `between(prev, next)`.
 */
export function planMove<T extends Positioned>(
  target: readonly T[],
  movingId: T['id'],
  index: number,
): MovePlan<T['id']> {
  const rest = target.filter((item) => item.id !== movingId)
  const at = Math.max(0, Math.min(Math.trunc(index), rest.length))
  const prev = at > 0 ? rest[at - 1] : undefined
  const next = at < rest.length ? rest[at] : undefined
  const currentIndex = target.findIndex((item) => item.id === movingId)
  return {
    prevId: prev?.id ?? null,
    nextId: next?.id ?? null,
    position: between(prev?.position, next?.position),
    unchanged: currentIndex === at,
  }
}
