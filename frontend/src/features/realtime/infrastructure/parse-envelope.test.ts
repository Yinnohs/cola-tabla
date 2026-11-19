import { describe, expect, it } from 'vitest'
import { parseEnvelope, parseEnvelopeJson } from './parse-envelope'

const BOARD = '6f1c1b0e-8a7d-4c43-9d57-2f0a3d1f0001'
const LIST = 'a1b2c3d4-0000-4000-8000-000000000003'
const FROM = 'a1b2c3d4-0000-4000-8000-000000000004'
const CARD = '0b6d8d1e-7d0f-4b0d-8a6e-6a4c5f3a0002'

const cardSummary = {
  id: CARD,
  listId: LIST,
  title: 'Preparar demo',
  description: null,
  position: 'V',
  version: 3,
  createdAt: '2026-11-18T10:00:00Z',
}

// Ejemplo literal de docs/contracts/board-events.md
const movedJson = {
  boardId: BOARD,
  seq: 5,
  type: 'card.moved',
  payload: { card: cardSummary, fromListId: FROM },
  clientId: 'tab-7f3a',
  occurredAt: '2026-11-18T10:05:00.123Z',
  schemaVersion: 1,
}

describe('parseEnvelope', () => {
  it('mapea el ejemplo de card.moved del contrato (descripcion null -> vacia)', () => {
    const e = parseEnvelope(movedJson)
    expect(e).toMatchObject({
      boardId: BOARD,
      seq: 5,
      type: 'card.moved',
      clientId: 'tab-7f3a',
      schemaVersion: 1,
    })
    if (e.type !== 'card.moved') throw new Error('tipo inesperado')
    expect(e.payload.fromListId).toBe(FROM)
    expect(e.payload.card).toEqual({
      id: CARD,
      listId: LIST,
      title: 'Preparar demo',
      description: '',
      position: 'V',
      version: 3,
    })
  })

  it('mapea todos los tipos del contrato', () => {
    const list = { id: LIST, boardId: BOARD, name: 'Todo', position: 'V', version: 1 }
    const board = { id: BOARD, workspaceId: FROM, name: 'B', version: 2 }
    const base = { boardId: BOARD, seq: 1, clientId: null, occurredAt: 'x', schemaVersion: 1 }
    const cases: Array<[string, unknown]> = [
      ['board.updated', { board }],
      ['list.created', { list }],
      ['list.updated', { list }],
      ['list.moved', { list }],
      ['card.created', { card: cardSummary }],
      ['card.updated', { card: cardSummary }],
      ['card.deleted', { cardId: CARD, listId: LIST }],
    ]
    for (const [type, payload] of cases) {
      expect(parseEnvelope({ ...base, type, payload }).type).toBe(type)
    }
  })

  it('un clientId invalido se descarta (null)', () => {
    expect(parseEnvelope({ ...movedJson, clientId: 'con espacios' }).clientId).toBeNull()
    expect(parseEnvelope({ ...movedJson, clientId: null }).clientId).toBeNull()
  })

  it('tipo desconocido, schemaVersion distinta o payload invalido -> unknown conservando seq', () => {
    const futureType = parseEnvelope({ ...movedJson, type: 'list.deleted', payload: {} })
    expect(futureType).toMatchObject({ type: 'unknown', seq: 5, rawType: 'list.deleted' })
    expect(parseEnvelope({ ...movedJson, schemaVersion: 2 })).toMatchObject({
      type: 'unknown',
      seq: 5,
    })
    expect(parseEnvelope({ ...movedJson, payload: { card: { id: 1 } } })).toMatchObject({
      type: 'unknown',
      seq: 5,
    })
  })

  it('envelope malformado lanza', () => {
    expect(() => parseEnvelope(null)).toThrow()
    expect(() => parseEnvelope({ ...movedJson, seq: 0 })).toThrow()
    expect(() => parseEnvelope({ ...movedJson, seq: '5' })).toThrow()
    expect(() => parseEnvelope({ ...movedJson, boardId: '' })).toThrow()
    expect(() => parseEnvelopeJson('no es json')).toThrow()
  })

  it('parseEnvelopeJson lee el texto del frame', () => {
    expect(parseEnvelopeJson(JSON.stringify(movedJson)).seq).toBe(5)
  })
})
