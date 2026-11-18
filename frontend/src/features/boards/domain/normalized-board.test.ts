import { describe, expect, it } from 'vitest'
import { boardId, cardId, fractionalIndex, listId, workspaceId } from '../../../shared/kernel'
import type { BoardCardView, BoardDetails, BoardListView } from './board-details'

// El dominio no puede importar shared/testing: factories locales.
let n = 0
const id = () => `id-${++n}`
const aCardView = (o: Partial<BoardCardView> = {}): BoardCardView => ({
  id: cardId(id()),
  listId: listId(id()),
  title: 'Card',
  description: '',
  position: fractionalIndex('V'),
  version: 0,
  ...o,
})
const aListView = (o: Partial<BoardListView> = {}): BoardListView => ({
  id: listId(id()),
  name: 'List',
  position: fractionalIndex('V'),
  version: 0,
  cards: [],
  ...o,
})
const aBoardDetails = (o: Partial<BoardDetails> = {}): BoardDetails => ({
  board: { id: boardId(id()), workspaceId: workspaceId(id()), name: 'Board', version: 0 },
  lists: [],
  ...o,
})
import { normalizeBoard, selectCardsOf, selectLists } from './normalized-board'

function sample() {
  const l1 = aListView({ name: 'Todo', position: fractionalIndex('V') })
  const l0 = aListView({ name: 'Backlog', position: fractionalIndex('G') })
  const c2 = aCardView({ title: 'b', position: fractionalIndex('k'), listId: l1.id })
  const c1 = aCardView({ title: 'a', position: fractionalIndex('F'), listId: l1.id })
  const details = aBoardDetails({
    lists: [{ ...l1, cards: [c2, c1] }, l0],
  })
  return { details, l0, l1, c1, c2 }
}

describe('normalizeBoard', () => {
  it('ordena listas y tarjetas por posicion y normaliza por id', () => {
    const { details, l0, l1, c1, c2 } = sample()
    const nb = normalizeBoard(details)
    expect(nb.listIds).toEqual([l0.id, l1.id])
    expect(nb.lists[l1.id]?.cardIds).toEqual([c1.id, c2.id])
    expect(nb.lists[l1.id]).not.toHaveProperty('cards')
    expect(nb.cards[c1.id]?.title).toBe('a')
    expect(nb.cards[c2.id]?.listId).toBe(l1.id)
    expect(nb.board).toBe(details.board)
  })

  it('selectLists / selectCardsOf reconstruyen la vista ordenada', () => {
    const { details, l1 } = sample()
    const nb = normalizeBoard(details)
    expect(selectLists(nb).map((l) => l.name)).toEqual(['Backlog', 'Todo'])
    expect(selectLists(nb)[1]?.cards.map((c) => c.title)).toEqual(['a', 'b'])
    expect(selectCardsOf(nb, l1.id).map((c) => c.title)).toEqual(['a', 'b'])
    expect(selectCardsOf(nb, listId('nope'))).toEqual([])
  })

  it('tablero vacio', () => {
    const nb = normalizeBoard(aBoardDetails())
    expect(nb.listIds).toEqual([])
    expect(selectLists(nb)).toEqual([])
  })
})
