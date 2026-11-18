import { describe, expect, it } from 'vitest'
import { boardId, cardId, fractionalIndex, listId, workspaceId } from '../../../shared/kernel'
import {
  normalizeBoard,
  removeCard,
  selectCardsOf,
  upsertCard,
  upsertList,
  type NormalizedBoard,
} from './normalized-board'

const L1 = listId('l1')
const L2 = listId('l2')
const [A, B, C] = ['a', 'b', 'c'].map(cardId)
const card = (id: typeof A, listId: typeof L1, position: string) => ({
  id: id!,
  listId,
  title: String(id),
  description: '',
  position: fractionalIndex(position),
  version: 0,
})

function sample(): NormalizedBoard {
  return normalizeBoard({
    board: { id: boardId('b'), workspaceId: workspaceId('w'), name: 'B', version: 0 },
    lists: [
      {
        id: L1,
        name: 'Todo',
        position: fractionalIndex('G'),
        version: 0,
        cards: [card(A, L1, 'G'), card(B, L1, 'V')],
      },
      { id: L2, name: 'Doing', position: fractionalIndex('V'), version: 0, cards: [] },
    ],
  })
}

describe('upsertCard', () => {
  it('reordena dentro de la lista al cambiar la posicion', () => {
    const next = upsertCard(sample(), card(A, L1, 'k'))
    expect(selectCardsOf(next, L1).map((c) => c.id)).toEqual([B, A])
  })

  it('mueve a otra lista: sale de la origen y entra ordenada en la destino', () => {
    const moved = upsertCard(sample(), card(A, L2, 'V'))
    expect(selectCardsOf(moved, L1).map((c) => c.id)).toEqual([B])
    expect(selectCardsOf(moved, L2).map((c) => c.id)).toEqual([A])
    expect(moved.cards[A!]?.listId).toBe(L2)
  })

  it('inserta una tarjeta nueva y no muta el original', () => {
    const base = sample()
    const next = upsertCard(base, card(C, L1, 'k'))
    expect(selectCardsOf(next, L1).map((c) => c.id)).toEqual([A, B, C])
    expect(base.lists[L1]?.cardIds).toEqual([A, B])
  })

  it('ignora tarjetas de listas desconocidas', () => {
    const base = sample()
    expect(upsertCard(base, card(C, listId('nope'), 'k'))).toBe(base)
  })
})

describe('removeCard', () => {
  it('quita la tarjeta de la lista y de las entidades', () => {
    const next = removeCard(sample(), A!)
    expect(next.cards[A!]).toBeUndefined()
    expect(next.lists[L1]?.cardIds).toEqual([B])
  })
})

describe('upsertList', () => {
  it('reordena listIds por posicion y conserva las tarjetas', () => {
    const base = sample()
    const next = upsertList(base, {
      id: L1,
      name: 'Todo',
      position: fractionalIndex('k'),
      version: 1,
    })
    expect(next.listIds).toEqual([L2, L1])
    expect(next.lists[L1]?.cardIds).toEqual([A, B])
    expect(next.lists[L1]?.version).toBe(1)
  })

  it('anade una lista nueva sin tarjetas', () => {
    const next = upsertList(sample(), {
      id: listId('l3'),
      name: 'Done',
      position: fractionalIndex('k'),
      version: 0,
    })
    expect(next.listIds).toEqual([L1, L2, listId('l3')])
    expect(next.lists[listId('l3')]?.cardIds).toEqual([])
  })
})
