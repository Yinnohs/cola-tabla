import { describe, expect, it } from 'vitest'
import { compareFractionalIndex, fractionalIndex } from './fractional-index'
import { planMove } from './ordering'

const items = [
  { id: 'a', position: fractionalIndex('G') },
  { id: 'b', position: fractionalIndex('V') },
  { id: 'c', position: fractionalIndex('k') },
]

describe('planMove', () => {
  it('inserta entre dos vecinos con una posicion estrictamente intermedia', () => {
    const plan = planMove(items, 'c', 1)
    expect(plan.prevId).toBe('a')
    expect(plan.nextId).toBe('b')
    expect(compareFractionalIndex('G', plan.position)).toBe(-1)
    expect(compareFractionalIndex(plan.position, 'V')).toBe(-1)
    expect(plan.unchanged).toBe(false)
  })

  it('al principio no tiene prev; al final no tiene next', () => {
    const first = planMove(items, 'c', 0)
    expect([first.prevId, first.nextId]).toEqual([null, 'a'])
    expect(compareFractionalIndex(first.position, 'G')).toBe(-1)
    const last = planMove(items, 'a', 99)
    expect([last.prevId, last.nextId]).toEqual(['c', null])
    expect(compareFractionalIndex('k', last.position)).toBe(-1)
  })

  it('lista destino vacia: sin vecinos', () => {
    const plan = planMove<{ id: string; position: string }>([], 'x', 0)
    expect([plan.prevId, plan.nextId]).toEqual([null, null])
  })

  it('soltar donde ya esta es unchanged', () => {
    expect(planMove(items, 'b', 1).unchanged).toBe(true)
    expect(planMove(items, 'a', 0).unchanged).toBe(true)
    expect(planMove(items, 'c', 2).unchanged).toBe(true)
  })

  it('un indice negativo se acota al principio', () => {
    expect(planMove(items, 'c', -5).nextId).toBe('a')
  })

  it('el elemento movido que viene de otra lista cuenta como cambio', () => {
    expect(planMove(items, 'z', 1).unchanged).toBe(false)
  })
})
