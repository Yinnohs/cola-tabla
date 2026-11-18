import type { Ports } from '../../app/di'
import { between, type FractionalIndex } from '../kernel'
import { AppError, StaleVersionError } from '../lib'

export function fakeAuthPort(token: string | null = 'test-token'): Ports['auth'] {
  return { getToken: () => token }
}

// Tipos de dominio derivados de los puertos: shared/ no puede importar features/.
type WorkspaceRepo = Ports['workspaces']
type BoardRepo = Ports['boards']
type ListRepo = Ports['lists']
type CardRepo = Ports['cards']
export type Workspace = Awaited<ReturnType<WorkspaceRepo['create']>>
export type Board = Awaited<ReturnType<BoardRepo['create']>>
export type BoardDetails = Awaited<ReturnType<BoardRepo['get']>>
export type List = Awaited<ReturnType<ListRepo['create']>>
export type Card = Awaited<ReturnType<CardRepo['create']>>

let seq = 0
export function nextId(): string {
  seq += 1
  return `00000000-0000-4000-8000-${String(seq).padStart(12, '0')}`
}

// --- Factories ---

export function aWorkspace(overrides: Partial<Workspace> = {}): Workspace {
  return {
    id: nextId() as Workspace['id'],
    name: 'Workspace',
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

export function aBoard(overrides: Partial<Board> = {}): Board {
  return {
    id: nextId() as Board['id'],
    workspaceId: nextId() as Board['workspaceId'],
    name: 'Board',
    version: 0,
    ...overrides,
  }
}

export type BoardListView = BoardDetails['lists'][number]
export type BoardCardView = BoardListView['cards'][number]

export function aCardView(overrides: Partial<BoardCardView> = {}): BoardCardView {
  return {
    id: nextId() as BoardCardView['id'],
    listId: nextId() as BoardCardView['listId'],
    title: 'Card',
    description: '',
    position: 'V' as BoardCardView['position'],
    version: 0,
    ...overrides,
  }
}

export function aListView(overrides: Partial<BoardListView> = {}): BoardListView {
  return {
    id: nextId() as BoardListView['id'],
    name: 'List',
    position: 'V' as BoardListView['position'],
    version: 0,
    cards: [],
    ...overrides,
  }
}

export function aBoardDetails(overrides: Partial<BoardDetails> = {}): BoardDetails {
  return { board: aBoard(), lists: [], ...overrides }
}

// --- Fakes en memoria ---

export class InMemoryWorkspaceRepository implements WorkspaceRepo {
  items: Workspace[]
  /** Si se define, el siguiente metodo llamado rechaza con este error (una sola vez). */
  failNextWith?: Error

  constructor(initial: Workspace[] = []) {
    this.items = [...initial]
  }

  list(): Promise<Workspace[]> {
    return this.settle(() => [...this.items])
  }

  get(id: Workspace['id']): Promise<Workspace> {
    return this.settle(() => {
      const found = this.items.find((w) => w.id === id)
      if (!found) throw new AppError(`Workspace ${id} not found`, 404, 'NOT_FOUND')
      return found
    })
  }

  create(name: string): Promise<Workspace> {
    return this.settle(() => {
      const w = aWorkspace({ name })
      this.items.push(w)
      return w
    })
  }

  private settle<T>(fn: () => T): Promise<T> {
    if (this.failNextWith) {
      const e = this.failNextWith
      this.failNextWith = undefined
      return Promise.reject(e)
    }
    try {
      return Promise.resolve(fn())
    } catch (e) {
      return Promise.reject(e instanceof Error ? e : new Error(String(e)))
    }
  }
}

export class InMemoryBoardRepository implements BoardRepo {
  boards = new Map<string, BoardDetails>()
  failNextWith?: Error

  constructor(initial: BoardDetails[] = []) {
    for (const d of initial) this.boards.set(d.board.id, d)
  }

  listByWorkspace(workspaceId: Board['workspaceId']): Promise<Board[]> {
    return this.settle(() =>
      [...this.boards.values()].map((d) => d.board).filter((b) => b.workspaceId === workspaceId),
    )
  }

  get(id: Board['id']): Promise<BoardDetails> {
    return this.settle(() => {
      const found = this.boards.get(id)
      if (!found) throw new Error(`Board ${id} not found`)
      return found
    })
  }

  create(workspaceId: Board['workspaceId'], name: string): Promise<Board> {
    return this.settle(() => {
      const board = aBoard({ workspaceId, name })
      this.boards.set(board.id, { board, lists: [] })
      return board
    })
  }

  rename(id: Board['id'], name: string, version: number): Promise<Board> {
    return this.settle(() => {
      const found = this.boards.get(id)
      if (!found) throw new Error(`Board ${id} not found`)
      if (found.board.version !== version) throw new Error('STALE_VERSION')
      const board = { ...found.board, name, version: version + 1 }
      this.boards.set(id, { ...found, board })
      return board
    })
  }

  private settle<T>(fn: () => T): Promise<T> {
    if (this.failNextWith) {
      const e = this.failNextWith
      this.failNextWith = undefined
      return Promise.reject(e)
    }
    try {
      return Promise.resolve(fn())
    } catch (e) {
      return Promise.reject(e instanceof Error ? e : new Error(String(e)))
    }
  }
}

/** Base comun de los fakes: fallo puntual y compuerta para observar el estado optimista. */
abstract class FakeRepository {
  /** Si se define, el siguiente metodo llamado rechaza con este error (una sola vez). */
  failNextWith?: Error
  private gate?: Promise<void>

  /** La siguiente llamada queda pendiente hasta que se invoque la funcion devuelta. */
  holdNext(): () => void {
    let release!: () => void
    this.gate = new Promise<void>((resolve) => {
      release = resolve
    })
    return release
  }

  protected async settle<T>(fn: () => T): Promise<T> {
    const gate = this.gate
    this.gate = undefined
    if (gate) await gate
    if (this.failNextWith) {
      const e = this.failNextWith
      this.failNextWith = undefined
      throw e
    }
    return fn()
  }
}

function orderedBetween(
  items: Array<{ id: string; position: string }>,
  prevId: string | null | undefined,
  nextId: string | null | undefined,
): FractionalIndex {
  const prev = prevId ? items.find((i) => i.id === prevId) : undefined
  const next = nextId ? items.find((i) => i.id === nextId) : undefined
  return between(prev?.position, next?.position)
}

export class InMemoryListRepository extends FakeRepository implements ListRepo {
  items: List[]
  /** Peticiones de movimiento recibidas (vecinos enviados al servidor). */
  moves: Array<{ id: List['id']; prevListId: List['id'] | null; nextListId: List['id'] | null }> =
    []

  constructor(initial: List[] = []) {
    super()
    this.items = [...initial]
  }

  create(boardId: List['boardId'], name: string): Promise<List> {
    return this.settle(() => {
      const last = [...this.items].sort((a, b) => (a.position < b.position ? -1 : 1)).at(-1)
      const list: List = {
        id: nextId() as List['id'],
        boardId,
        name,
        position: between(last?.position, null),
        version: 0,
      }
      this.items.push(list)
      return list
    })
  }

  rename(id: List['id'], name: string, version: number): Promise<List> {
    return this.settle(() => {
      const found = this.require(id)
      if (found.version !== version) throw new StaleVersionError('STALE_VERSION')
      const updated = { ...found, name, version: version + 1 }
      this.replace(updated)
      return updated
    })
  }

  move(id: List['id'], neighbours: Parameters<ListRepo['move']>[1]): Promise<List> {
    return this.settle(() => {
      this.moves.push({ id, ...neighbours })
      const found = this.require(id)
      const others = this.items.filter((l) => l.id !== id)
      const position = orderedBetween(others, neighbours.prevListId, neighbours.nextListId)
      const updated = { ...found, position, version: found.version + 1 }
      this.replace(updated)
      return updated
    })
  }

  private require(id: List['id']): List {
    const found = this.items.find((l) => l.id === id)
    if (!found) throw new Error(`List ${id} not found`)
    return found
  }

  private replace(list: List) {
    this.items = this.items.map((l) => (l.id === list.id ? list : l))
  }
}

export class InMemoryCardRepository extends FakeRepository implements CardRepo {
  items: Card[]
  /** Peticiones de movimiento recibidas (vecinos enviados al servidor). */
  moves: Array<{
    id: Card['id']
    listId: Card['listId']
    prevCardId: Card['id'] | null
    nextCardId: Card['id'] | null
  }> = []

  constructor(initial: Card[] = []) {
    super()
    this.items = [...initial]
  }

  create(listId: Card['listId'], title: string, description?: string): Promise<Card> {
    return this.settle(() => {
      const inList = this.items.filter((c) => c.listId === listId)
      const last = [...inList].sort((a, b) => (a.position < b.position ? -1 : 1)).at(-1)
      const card: Card = {
        id: nextId() as Card['id'],
        listId,
        title,
        description: description ?? '',
        position: between(last?.position, null),
        version: 0,
      }
      this.items.push(card)
      return card
    })
  }

  update(id: Card['id'], update: Parameters<CardRepo['update']>[1]): Promise<Card> {
    return this.settle(() => {
      const found = this.require(id)
      if (found.version !== update.version) throw new StaleVersionError('STALE_VERSION')
      const updated = {
        ...found,
        title: update.title,
        description: update.description,
        version: found.version + 1,
      }
      this.replace(updated)
      return updated
    })
  }

  move(id: Card['id'], request: Parameters<CardRepo['move']>[1]): Promise<Card> {
    return this.settle(() => {
      this.moves.push({ id, ...request })
      const found = this.require(id)
      const others = this.items.filter((c) => c.listId === request.listId && c.id !== id)
      const position = orderedBetween(others, request.prevCardId, request.nextCardId)
      const updated = { ...found, listId: request.listId, position, version: found.version + 1 }
      this.replace(updated)
      return updated
    })
  }

  delete(id: Card['id']): Promise<void> {
    return this.settle(() => {
      this.require(id)
      this.items = this.items.filter((c) => c.id !== id)
    })
  }

  /** Simula que otra persona edito la tarjeta en el servidor (sube la version). */
  editRemotely(id: Card['id'], changes: Partial<Pick<Card, 'title' | 'description'>>): Card {
    const updated = { ...this.require(id), ...changes }
    updated.version += 1
    this.replace(updated)
    return updated
  }

  private require(id: Card['id']): Card {
    const found = this.items.find((c) => c.id === id)
    if (!found) throw new Error(`Card ${id} not found`)
    return found
  }

  private replace(card: Card) {
    this.items = this.items.map((c) => (c.id === card.id ? card : c))
  }
}

/** Repos en memoria coherentes entre si a partir de un tablero (listas y tarjetas sembradas). */
export function fakeBoardPorts(details: BoardDetails): {
  boards: InMemoryBoardRepository
  lists: InMemoryListRepository
  cards: InMemoryCardRepository
} {
  const lists: List[] = details.lists.map((l) => ({
    id: l.id,
    boardId: details.board.id,
    name: l.name,
    position: l.position,
    version: l.version,
  }))
  const cards: Card[] = details.lists.flatMap((l) => l.cards.map((c) => ({ ...c, listId: l.id })))
  const listRepo = new InMemoryListRepository(lists)
  const cardRepo = new InMemoryCardRepository(cards)
  const boardRepo = new InMemoryBoardRepository([details])
  // GET del tablero refleja el estado actual de las listas/tarjetas (para probar refetch tras conflictos).
  boardRepo.get = () => {
    const byPosition = (a: { position: string }, b: { position: string }) =>
      a.position < b.position ? -1 : a.position > b.position ? 1 : 0
    return Promise.resolve({
      board: details.board,
      lists: [...listRepo.items].sort(byPosition).map((l) => ({
        id: l.id,
        name: l.name,
        position: l.position,
        version: l.version,
        cards: cardRepo.items.filter((c) => c.listId === l.id).sort(byPosition),
      })),
    })
  }
  return { boards: boardRepo, lists: listRepo, cards: cardRepo }
}

/** Puertos fake en memoria; cada test puede sobreescribir los que necesite. */
export function fakePorts(overrides: Partial<Ports> = {}): Ports {
  return {
    auth: fakeAuthPort(),
    workspaces: new InMemoryWorkspaceRepository(),
    boards: new InMemoryBoardRepository(),
    lists: new InMemoryListRepository(),
    cards: new InMemoryCardRepository(),
    ...overrides,
  }
}
