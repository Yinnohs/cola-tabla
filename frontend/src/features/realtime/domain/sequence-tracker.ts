/** Que hacer con un evento segun su `seq` respecto al ultimo reflejado en la cache. */
export type SequenceDecision = 'APPLY' | 'IGNORE_DUPLICATE' | 'RESYNC'

/**
 * Algoritmo del contrato (board-events.md, "Orden y seq"):
 * - `seq == lastSeq + 1`: aplicar y avanzar.
 * - `seq <= lastSeq`: duplicado o ya incluido en la carga: ignorar.
 * - `seq > lastSeq + 1`: hay un hueco: recargar el tablero completo.
 */
export function decideSequence(lastSeq: number, seq: number): SequenceDecision {
  if (seq <= lastSeq) return 'IGNORE_DUPLICATE'
  if (seq === lastSeq + 1) return 'APPLY'
  return 'RESYNC'
}
