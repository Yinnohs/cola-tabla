import { describe, expect, it } from 'vitest'
import fc from 'fast-check'
import vectors from '../../../../docs/contracts/fractional-index-vectors.json'
import {
  ALPHABET,
  EXHAUSTION_LENGTH,
  FractionalIndexError,
  between,
  compareFractionalIndex,
  evenlySpaced,
  fractionalIndex,
  isExhausted,
  isValidFractionalIndex,
} from './fractional-index'

describe('FractionalIndex contract vectors', () => {
  it('alfabeto y umbral de agotamiento coinciden con el contrato', () => {
    expect(ALPHABET).toBe(vectors.alphabet)
    expect(EXHAUSTION_LENGTH).toBe(vectors.exhaustionLength)
  })

  it.each(vectors.between)('between($prev, $next) = $expected', ({ prev, next, expected }) => {
    expect(between(prev, next)).toBe(expected)
  })

  it.each(vectors.evenlySpaced)('evenlySpaced($count)', ({ count, expected }) => {
    expect(evenlySpaced(count)).toEqual(expected)
  })

  it.each(vectors.invalid)('rechaza valor invalido %j', (value) => {
    expect(isValidFractionalIndex(value)).toBe(false)
    expect(() => fractionalIndex(value)).toThrow(FractionalIndexError)
  })

  it('orderedAscending esta ordenado y es valido', () => {
    const list = vectors.orderedAscending
    list.forEach((v) => expect(isValidFractionalIndex(v)).toBe(true))
    for (let i = 1; i < list.length; i++) {
      expect(compareFractionalIndex(list[i - 1]!, list[i]!)).toBeLessThan(0)
    }
  })
})

describe('FractionalIndex semantics', () => {
  it('prev >= next es error', () => {
    expect(() => between('V', 'V')).toThrow(FractionalIndexError)
    expect(() => between('k', 'V')).toThrow(FractionalIndexError)
  })

  it('rechaza entradas invalidas en between', () => {
    expect(() => between('V0', null)).toThrow(FractionalIndexError)
  })

  it('evenlySpaced(0) es vacio y negativo falla', () => {
    expect(evenlySpaced(0)).toEqual([])
    expect(() => evenlySpaced(-1)).toThrow(FractionalIndexError)
  })

  it('isExhausted a partir de EXHAUSTION_LENGTH', () => {
    expect(isExhausted('V'.repeat(EXHAUSTION_LENGTH - 1))).toBe(false)
    expect(isExhausted('V'.repeat(EXHAUSTION_LENGTH))).toBe(true)
  })

  it('inserciones repetidas al mismo hueco agotan la clave', () => {
    let prev: string = 'V'
    let steps = 0
    while (!isExhausted(prev) && steps < 1000) {
      prev = between(prev, null)
      steps++
    }
    expect(isExhausted(prev)).toBe(true)
  })
})

const validKey = fc
  .array(fc.integer({ min: 0, max: ALPHABET.length - 1 }), { minLength: 1, maxLength: 12 })
  .map((ds) => {
    const s = ds.map((d) => ALPHABET.charAt(d)).join('')
    return s.replace(/0+$/, '') || 'V'
  })

describe('FractionalIndex properties', () => {
  it('between(a, b) esta estrictamente entre a y b', () => {
    fc.assert(
      fc.property(validKey, validKey, (x, y) => {
        fc.pre(x !== y)
        const [lo, hi] = x < y ? [x, y] : [y, x]
        const mid = between(lo, hi)
        expect(isValidFractionalIndex(mid)).toBe(true)
        expect(mid > lo).toBe(true)
        expect(mid < hi).toBe(true)
      }),
      { numRuns: 500 },
    )
  })

  it('between con limites abiertos queda del lado correcto', () => {
    fc.assert(
      fc.property(validKey, (x) => {
        expect(between(x, null) > x).toBe(true)
        expect(between(null, x) < x).toBe(true)
      }),
      { numRuns: 500 },
    )
  })

  it('evenlySpaced es estrictamente creciente y valido', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 300 }), (n) => {
        const xs = evenlySpaced(n)
        expect(xs).toHaveLength(n)
        for (let i = 0; i < xs.length; i++) {
          expect(isValidFractionalIndex(xs[i]!)).toBe(true)
          if (i > 0) expect(xs[i]! > xs[i - 1]!).toBe(true)
        }
      }),
      { numRuns: 100 },
    )
  })

  it('secuencia aleatoria de inserciones mantiene la lista ordenada', () => {
    fc.assert(
      fc.property(fc.array(fc.nat({ max: 1000 }), { minLength: 1, maxLength: 60 }), (picks) => {
        const list: string[] = []
        for (const p of picks) {
          const i = p % (list.length + 1)
          list.splice(i, 0, between(list[i - 1] ?? null, list[i] ?? null))
        }
        for (let i = 1; i < list.length; i++) expect(list[i]! > list[i - 1]!).toBe(true)
      }),
      { numRuns: 200 },
    )
  })
})
