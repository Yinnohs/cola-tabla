import {
  boardId,
  cardId,
  fractionalIndex,
  isClientId,
  listId,
  workspaceId,
} from '../../../shared/kernel'
import {
  SUPPORTED_SCHEMA_VERSION,
  type BoardEnvelope,
  type BoardEvent,
  type BoardPayload,
  type CardPayload,
  type ListPayload,
} from '../domain/board-envelope'

type Json = Record<string, unknown>

const isObject = (v: unknown): v is Json => typeof v === 'object' && v !== null && !Array.isArray(v)
const str = (v: unknown, what: string): string => {
  if (typeof v !== 'string' || v === '') throw new Error(`campo invalido: ${what}`)
  return v
}
const int = (v: unknown, what: string): number => {
  if (typeof v !== 'number' || !Number.isInteger(v)) throw new Error(`campo invalido: ${what}`)
  return v
}
const obj = (v: unknown, what: string): Json => {
  if (!isObject(v)) throw new Error(`campo invalido: ${what}`)
  return v
}

function toBoardPayload(raw: unknown): BoardPayload {
  const b = obj(raw, 'board')
  return {
    id: boardId(str(b.id, 'board.id')),
    workspaceId: workspaceId(str(b.workspaceId, 'board.workspaceId')),
    name: str(b.name, 'board.name'),
    version: int(b.version, 'board.version'),
  }
}

function toListPayload(raw: unknown): ListPayload {
  const l = obj(raw, 'list')
  return {
    id: listId(str(l.id, 'list.id')),
    boardId: boardId(str(l.boardId, 'list.boardId')),
    name: str(l.name, 'list.name'),
    position: fractionalIndex(str(l.position, 'list.position')),
    version: int(l.version, 'list.version'),
  }
}

function toCardPayload(raw: unknown): CardPayload {
  const c = obj(raw, 'card')
  return {
    id: cardId(str(c.id, 'card.id')),
    listId: listId(str(c.listId, 'card.listId')),
    title: str(c.title, 'card.title'),
    description: typeof c.description === 'string' ? c.description : '',
    position: fractionalIndex(str(c.position, 'card.position')),
    version: int(c.version, 'card.version'),
  }
}

function toEvent(type: string, payload: unknown): BoardEvent {
  const p = obj(payload, 'payload')
  switch (type) {
    case 'board.updated':
      return { type, payload: { board: toBoardPayload(p.board) } }
    case 'list.created':
    case 'list.updated':
    case 'list.moved':
      return { type, payload: { list: toListPayload(p.list) } }
    case 'card.created':
    case 'card.updated':
      return { type, payload: { card: toCardPayload(p.card) } }
    case 'card.moved':
      return {
        type,
        payload: {
          card: toCardPayload(p.card),
          fromListId: listId(str(p.fromListId, 'fromListId')),
        },
      }
    case 'card.deleted':
      return {
        type,
        payload: {
          cardId: cardId(str(p.cardId, 'cardId')),
          listId: listId(str(p.listId, 'listId')),
        },
      }
    default:
      return { type: 'unknown', rawType: type, payload }
  }
}

/**
 * JSON del contrato (docs/contracts/board-events.md) -> `BoardEnvelope` de dominio.
 * - Lanza si el envelope esta malformado (sin boardId/seq/type validos).
 * - Un `type` desconocido, un `schemaVersion` distinto o un payload no interpretable se devuelven como
 *   `type: 'unknown'` conservando `seq`: el consumidor recarga el tablero en vez de ignorarlo.
 */
export function parseEnvelope(raw: unknown): BoardEnvelope {
  const e = obj(raw, 'envelope')
  const seq = int(e.seq, 'seq')
  if (seq < 1) throw new Error('campo invalido: seq')
  const type = str(e.type, 'type')
  const meta = {
    boardId: boardId(str(e.boardId, 'boardId')),
    seq,
    clientId: isClientId(e.clientId) ? e.clientId : null,
    occurredAt: typeof e.occurredAt === 'string' ? e.occurredAt : '',
    schemaVersion: typeof e.schemaVersion === 'number' ? e.schemaVersion : 0,
  }
  if (meta.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
    return { ...meta, type: 'unknown', rawType: type, payload: e.payload }
  }
  try {
    return { ...meta, ...toEvent(type, e.payload) }
  } catch {
    return { ...meta, type: 'unknown', rawType: type, payload: e.payload }
  }
}

export function parseEnvelopeJson(text: string): BoardEnvelope {
  return parseEnvelope(JSON.parse(text) as unknown)
}
