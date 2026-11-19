import { describe, expect, it } from 'vitest'
import { aBoard, aBoardDetails, aCardView, aListView } from '../../../shared/testing'
import { normalizeBoard } from '../../boards'
import { applyListEvent } from './apply-list-event'

function seeded() {
  const a = aListView({ name: 'A', position: 'G' as never })
  const b = aListView({ name: 'B', position: 'V' as never })
  const card = aCardView({ listId: a.id })
  const nb = normalizeBoard(aBoardDetails({ board: aBoard(), lists: [{ ...a, cards: [card] }, b] }))
  const entity = (l: typeof a, extra: object = {}) => ({
    id: l.id,
    name: l.name,
    position: l.position,
    version: l.version,
    ...extra,
  })
  return { a, b, card, nb, entity }
}

describe('applyListEvent', () => {
  it('list.created anade la lista en su posicion', () => {
    const { nb, a, b } = seeded()
    const created = { id: aListView().id, name: 'N', position: 'M' as never, version: 0 }
    const next = applyListEvent(nb, { type: 'list.created', list: created })
    expect(next.listIds).toEqual([a.id, created.id, b.id])
  })

  it('list.updated renombra conservando las tarjetas', () => {
    const { nb, a, card, entity } = seeded()
    const next = applyListEvent(nb, {
      type: 'list.updated',
      list: entity(a, { name: 'A2', version: a.version + 1 }),
    })
    expect(next.lists[a.id]?.name).toBe('A2')
    expect(next.lists[a.id]?.cardIds).toEqual([card.id])
  })

  it('list.moved reordena listIds', () => {
    const { nb, a, b, entity } = seeded()
    const next = applyListEvent(nb, {
      type: 'list.moved',
      list: entity(a, { position: 'k', version: a.version + 1 }),
    })
    expect(next.listIds).toEqual([b.id, a.id])
  })

  it('un evento duplicado o con version anterior no cambia el estado', () => {
    const { nb, a, entity } = seeded()
    const next = applyListEvent(nb, {
      type: 'list.updated',
      list: entity(a, { name: 'A2', version: a.version + 2 }),
    })
    expect(
      applyListEvent(next, {
        type: 'list.updated',
        list: entity(a, { name: 'viejo', version: 1 }),
      }),
    ).toBe(next)
  })
})
