import { describe, expect, it } from 'vitest'
import { boardId, cardId, fractionalIndex, listId, workspaceId } from '../../../shared/kernel'
import { applyBoardEvent } from './apply-board-event'
import {
  normalizeBoard,
  reconcileCard,
  reconcileList,
  withLastSeq,
  type NormalizedBoard,
} from './normalized-board'

const B = boardId('b1')
const L = listId('l1')

function base(): NormalizedBoard {
  return normalizeBoard({
    board: { id: B, workspaceId: workspaceId('w'), name: 'Sprint', version: 2 },
    lastSeq: 10,
    lists: [{ id: L, name: 'Todo', position: fractionalIndex('V'), version: 1, cards: [] }],
  })
}

describe('applyBoardEvent', () => {
  it('aplica un board.updated mas nuevo y conserva listas y lastSeq', () => {
    const nb = base()
    const next = applyBoardEvent(nb, {
      type: 'board.updated',
      board: { ...nb.board, name: 'Sprint 2', version: 3 },
    })
    expect(next.board.name).toBe('Sprint 2')
    expect(next.lastSeq).toBe(10)
    expect(next.lists).toBe(nb.lists)
  })

  it('ignora versiones iguales o anteriores y tableros ajenos', () => {
    const nb = base()
    expect(applyBoardEvent(nb, { type: 'board.updated', board: { ...nb.board, version: 2 } })).toBe(
      nb,
    )
    expect(applyBoardEvent(nb, { type: 'board.updated', board: { ...nb.board, version: 1 } })).toBe(
      nb,
    )
    expect(
      applyBoardEvent(nb, {
        type: 'board.updated',
        board: { ...nb.board, id: boardId('otro'), version: 9 },
      }),
    ).toBe(nb)
  })
})

describe('withLastSeq / reconcile', () => {
  it('withLastSeq devuelve la misma referencia si no cambia', () => {
    const nb = base()
    expect(withLastSeq(nb, 10)).toBe(nb)
    expect(withLastSeq(nb, 11).lastSeq).toBe(11)
  })

  it('reconcileList y reconcileCard no retroceden a una version anterior', () => {
    const nb = base()
    const list = { id: L, name: 'viejo', position: fractionalIndex('V'), version: 0 }
    expect(reconcileList(nb, list)).toBe(nb)
    expect(reconcileList(nb, { ...list, name: 'nuevo', version: 2 }).lists[L]?.name).toBe('nuevo')
    const C = cardId('c1')
    const withCard = reconcileCard(nb, {
      id: C,
      listId: L,
      title: 'T',
      description: '',
      position: fractionalIndex('V'),
      version: 3,
    })
    const stale = { ...withCard.cards[C]!, title: 'viejo', version: 2 }
    expect(reconcileCard(withCard, stale)).toBe(withCard)
  })
})
