import { describe, expect, it } from 'vitest'
import { cardId, fractionalIndex, listId, compareFractionalIndex } from '../../../shared/kernel'
import { cardDescription, cardTitle } from './card'
import { planCardMove, resolveCardDrop } from './card-ordering'

const L1 = listId('l1')
const L2 = listId('l2')
const [A, B, C, X] = ['a', 'b', 'c', 'x'].map(cardId)
const pos = (id: string) => fractionalIndex(id === 'a' ? 'G' : id === 'b' ? 'V' : 'k')

describe('planCardMove', () => {
  const l1 = [A!, B!, C!].map((id) => ({ id, position: pos(id) }))

  it('mismo listado: reordena entre vecinos y devuelve ids de vecinos + posicion optimista', () => {
    const plan = planCardMove(l1, C!, L1, 1)
    expect(plan).toMatchObject({ cardId: C, toListId: L1, prevCardId: A, nextCardId: B })
    expect(compareFractionalIndex('G', plan.position)).toBe(-1)
    expect(compareFractionalIndex(plan.position, 'V')).toBe(-1)
    expect(plan.unchanged).toBe(false)
  })

  it('otra lista vacia: sin vecinos', () => {
    const plan = planCardMove([], X!, L2, 0)
    expect(plan).toMatchObject({ prevCardId: null, nextCardId: null, toListId: L2 })
  })

  it('a otra lista al final: prev = ultima, next = null', () => {
    const plan = planCardMove(l1, X!, L1, 3)
    expect(plan).toMatchObject({ prevCardId: C, nextCardId: null })
  })
})

describe('resolveCardDrop', () => {
  const byList = { [L1]: [A!, B!, C!], [L2]: [X!] }

  it('misma lista: bajar deja la tarjeta despues del objetivo; subir, antes', () => {
    expect(resolveCardDrop(byList, A!, { kind: 'card', cardId: B! })).toEqual({
      toListId: L1,
      index: 1,
    })
    expect(resolveCardDrop(byList, C!, { kind: 'card', cardId: A! })).toEqual({
      toListId: L1,
      index: 0,
    })
  })

  it('soltar sobre si misma no hace nada', () => {
    expect(resolveCardDrop(byList, A!, { kind: 'card', cardId: A! })).toBeNull()
  })

  it('entre listas: antes del objetivo, o despues si `below`', () => {
    expect(resolveCardDrop(byList, A!, { kind: 'card', cardId: X! })).toEqual({
      toListId: L2,
      index: 0,
    })
    expect(resolveCardDrop(byList, A!, { kind: 'card', cardId: X!, below: true })).toEqual({
      toListId: L2,
      index: 1,
    })
  })

  it('sobre una lista: al final (sin contar la propia tarjeta)', () => {
    expect(resolveCardDrop(byList, A!, { kind: 'list', listId: L2 })).toEqual({
      toListId: L2,
      index: 1,
    })
    expect(resolveCardDrop(byList, A!, { kind: 'list', listId: L1 })).toEqual({
      toListId: L1,
      index: 2,
    })
  })

  it('tarjeta o lista desconocida: null', () => {
    expect(resolveCardDrop(byList, cardId('zz'), { kind: 'list', listId: L1 })).toBeNull()
    expect(resolveCardDrop(byList, A!, { kind: 'list', listId: listId('nope') })).toBeNull()
  })
})

describe('validacion de tarjeta', () => {
  it('titulo: recorta, no vacio, max 255', () => {
    expect(cardTitle('  Hola ')).toEqual({ ok: true, value: 'Hola' })
    expect(cardTitle('   ')).toEqual({ ok: false, error: 'EMPTY' })
    expect(cardTitle('x'.repeat(256))).toEqual({ ok: false, error: 'TOO_LONG' })
  })

  it('descripcion: max 10000', () => {
    expect(cardDescription('')).toEqual({ ok: true, value: '' })
    expect(cardDescription('x'.repeat(10_001))).toEqual({ ok: false, error: 'TOO_LONG' })
  })
})
