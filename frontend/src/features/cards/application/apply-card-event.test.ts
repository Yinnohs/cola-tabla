import { describe, expect, it } from 'vitest'
import fc from 'fast-check'
import { aBoard, aBoardDetails, aCardView, aListView } from '../../../shared/testing'
import { normalizeBoard, selectCardsOf, type NormalizedBoard } from '../../boards'
import { applyCardEvent } from './apply-card-event'

function seeded() {
  const todo = aListView({ name: 'Todo', position: 'G' as never })
  const doing = aListView({ name: 'Doing', position: 'V' as never })
  const a = aCardView({ title: 'A', listId: todo.id, position: 'G' as never })
  const b = aCardView({ title: 'B', listId: todo.id, position: 'V' as never })
  const nb = normalizeBoard(
    aBoardDetails({ board: aBoard(), lists: [{ ...todo, cards: [a, b] }, doing] }),
  )
  const titles = (n: NormalizedBoard, listId: typeof todo.id) =>
    selectCardsOf(n, listId).map((c) => c.title)
  return { todo, doing, a, b, nb, titles }
}

describe('applyCardEvent', () => {
  it('card.created inserta la tarjeta en su lista, ordenada por posicion', () => {
    const { nb, todo, titles } = seeded()
    const created = aCardView({ title: 'N', listId: todo.id, position: 'M' as never, version: 0 })
    const next = applyCardEvent(nb, { type: 'card.created', card: created })
    expect(titles(next, todo.id)).toEqual(['A', 'N', 'B'])
  })

  it('card.updated aplica la version nueva; una duplicada o anterior no cambia nada', () => {
    const { nb, a, todo, titles } = seeded()
    const updated = applyCardEvent(nb, {
      type: 'card.updated',
      card: { ...a, title: 'A2', version: a.version + 1 },
    })
    expect(titles(updated, todo.id)).toEqual(['A2', 'B'])
    const duplicate = applyCardEvent(updated, {
      type: 'card.updated',
      card: { ...a, title: 'A2', version: a.version + 1 },
    })
    expect(duplicate).toEqual(updated)
    const stale = applyCardEvent(updated, { type: 'card.updated', card: { ...a, title: 'viejo' } })
    expect(stale).toBe(updated)
  })

  it('card.moved cambia de lista (sale de la origen) y reordena', () => {
    const { nb, a, todo, doing, titles } = seeded()
    const next = applyCardEvent(nb, {
      type: 'card.moved',
      card: { ...a, listId: doing.id, position: 'V' as never, version: a.version + 1 },
      fromListId: todo.id,
    })
    expect(titles(next, todo.id)).toEqual(['B'])
    expect(titles(next, doing.id)).toEqual(['A'])
  })

  it('card.deleted elimina la tarjeta y es idempotente', () => {
    const { nb, a, todo, titles } = seeded()
    const once = applyCardEvent(nb, { type: 'card.deleted', cardId: a.id, listId: todo.id })
    expect(titles(once, todo.id)).toEqual(['B'])
    expect(once.cards[a.id]).toBeUndefined()
    expect(applyCardEvent(once, { type: 'card.deleted', cardId: a.id, listId: todo.id })).toBe(once)
  })

  it('no modifica la entrada (inmutabilidad) ni lastSeq', () => {
    const { nb, a, doing, todo } = seeded()
    const snapshot = JSON.stringify(nb)
    const next = applyCardEvent(nb, {
      type: 'card.moved',
      card: { ...a, listId: doing.id, version: 5 },
      fromListId: todo.id,
    })
    expect(JSON.stringify(nb)).toBe(snapshot)
    expect(next.lastSeq).toBe(nb.lastSeq)
  })

  it('propiedad: aplicar dos veces el mismo evento equivale a aplicarlo una vez (idempotencia)', () => {
    const { nb, a, todo, doing } = seeded()
    fc.assert(
      fc.property(
        fc.constantFrom('card.created', 'card.updated', 'card.moved', 'card.deleted'),
        fc.constantFrom(todo.id, doing.id),
        fc.constantFrom('A', 'G', 'M', 'V', 'k'),
        fc.integer({ min: 0, max: 5 }),
        (type, listId, position, version) => {
          const card = { ...a, listId, position: position as never, version }
          const event =
            type === 'card.deleted'
              ? ({ type, cardId: a.id, listId } as const)
              : type === 'card.moved'
                ? ({ type, card, fromListId: todo.id } as const)
                : ({ type, card } as const)
          const once = applyCardEvent(nb, event)
          expect(JSON.stringify(applyCardEvent(once, event))).toBe(JSON.stringify(once))
        },
      ),
    )
  })

  it('propiedad: ids de lista y tarjetas siempre coherentes tras cualquier secuencia de eventos', () => {
    const { nb, a, b, todo, doing } = seeded()
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            card: fc.constantFrom(a, b),
            listId: fc.constantFrom(todo.id, doing.id),
            position: fc.constantFrom('A', 'G', 'M', 'V', 'k'),
            version: fc.integer({ min: 1, max: 8 }),
            del: fc.boolean(),
          }),
          { maxLength: 12 },
        ),
        (steps) => {
          let state = nb
          for (const s of steps) {
            state = s.del
              ? applyCardEvent(state, { type: 'card.deleted', cardId: s.card.id, listId: s.listId })
              : applyCardEvent(state, {
                  type: 'card.moved',
                  card: {
                    ...s.card,
                    listId: s.listId,
                    position: s.position as never,
                    version: s.version,
                  },
                  fromListId: todo.id,
                })
          }
          const referenced = Object.values(state.lists).flatMap((l) => l.cardIds)
          expect([...referenced].sort()).toEqual(Object.keys(state.cards).sort())
          for (const list of Object.values(state.lists)) {
            for (const id of list.cardIds) expect(state.cards[id]?.listId).toBe(list.id)
          }
        },
      ),
    )
  })
})
