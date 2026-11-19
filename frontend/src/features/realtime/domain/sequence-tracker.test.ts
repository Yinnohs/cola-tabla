import { describe, expect, it } from 'vitest'
import fc from 'fast-check'
import { decideSequence } from './sequence-tracker'

describe('decideSequence', () => {
  it('el siguiente seq se aplica', () => {
    expect(decideSequence(41, 42)).toBe('APPLY')
    expect(decideSequence(0, 1)).toBe('APPLY')
  })

  it('duplicados y anteriores se ignoran', () => {
    expect(decideSequence(41, 41)).toBe('IGNORE_DUPLICATE')
    expect(decideSequence(41, 7)).toBe('IGNORE_DUPLICATE')
  })

  it('un hueco (41 -> 43) pide recargar', () => {
    expect(decideSequence(41, 43)).toBe('RESYNC')
  })

  it('propiedad: exactamente una decision segun la posicion de seq', () => {
    fc.assert(
      fc.property(fc.nat(1000), fc.integer({ min: 1, max: 2000 }), (last, seq) => {
        const d = decideSequence(last, seq)
        if (seq <= last) expect(d).toBe('IGNORE_DUPLICATE')
        else if (seq === last + 1) expect(d).toBe('APPLY')
        else expect(d).toBe('RESYNC')
      }),
    )
  })

  it('propiedad: aplicando solo los APPLY de cualquier secuencia el lastSeq nunca retrocede', () => {
    fc.assert(
      fc.property(fc.array(fc.integer({ min: 1, max: 30 })), (seqs) => {
        let last = 0
        for (const seq of seqs) {
          const before = last
          if (decideSequence(last, seq) === 'APPLY') last = seq
          expect(last).toBeGreaterThanOrEqual(before)
        }
      }),
    )
  })
})
