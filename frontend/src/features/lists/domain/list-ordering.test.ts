import { describe, expect, it } from 'vitest'
import { compareFractionalIndex, fractionalIndex, listId } from '../../../shared/kernel'
import { listName } from './list'
import { planListMove } from './list-ordering'

const [A, B, C] = ['a', 'b', 'c'].map(listId)
const lists = [
  { id: A!, position: fractionalIndex('G') },
  { id: B!, position: fractionalIndex('V') },
  { id: C!, position: fractionalIndex('k') },
]

describe('planListMove', () => {
  it('mover la primera al final: prev = ultima, next = null', () => {
    const plan = planListMove(lists, A!, 2)
    expect(plan).toMatchObject({ prevId: C, nextId: null, unchanged: false })
    expect(compareFractionalIndex('k', plan.position)).toBe(-1)
  })

  it('mover la ultima entre las dos primeras', () => {
    const plan = planListMove(lists, C!, 1)
    expect(plan).toMatchObject({ prevId: A, nextId: B })
  })

  it('misma posicion: unchanged', () => {
    expect(planListMove(lists, B!, 1).unchanged).toBe(true)
  })
})

describe('listName', () => {
  it('valida como el resto de nombres', () => {
    expect(listName(' Todo ')).toEqual({ ok: true, value: 'Todo' })
    expect(listName('')).toEqual({ ok: false, error: 'EMPTY' })
  })
})
