import type { Ports } from '../../app/di'
import { between, type FractionalIndex } from '../kernel'
import { AppError, StaleVersionError } from '../lib'

type AuthSessionValue = ReturnType<Ports['auth']['getSnapshot']>

export const SIGNED_IN: AuthSessionValue = {
  status: 'authenticated',
  user: { id: 'user-1', name: 'Ana Test', email: 'ana@test.local' },
}
export const SIGNED_OUT: AuthSessionValue = { status: 'unauthenticated', user: null }
export const SESSION_LOADING: AuthSessionValue = { status: 'loading', user: null }

/** Fake del puerto de autenticacion: sesion controlable desde el test y registro de las llamadas. */
type AuthPortType = Ports['auth']

export class FakeAuthPort implements AuthPortType {
  private session: AuthSessionValue
  private token: string | null
  private readonly listeners = new Set<() => void>()
  readonly loginCalls: string[] = []
  logoutCalls = 0
  refreshCalls = 0
  /** Resultado de la proxima renovacion. */
  refreshResult = true
  /** Ruta que devuelve completeLogin; si es un Error, rechaza. */
  callbackResult: string | Error = '/'
  loginFailure?: Error

  constructor(session: AuthSessionValue = SIGNED_IN, token: string | null = 'test-token') {
    this.session = session
    this.token = session.status === 'authenticated' ? token : null
  }

  readonly getSnapshot = (): AuthSessionValue => this.session

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  /** Cambia la sesion y avisa a los suscriptores (como haria el adapter real). */
  setSession(session: AuthSessionValue, token: string | null = 'test-token') {
    this.session = session
    this.token = session.status === 'authenticated' ? token : null
    this.listeners.forEach((l) => l())
  }

  initialize(): Promise<void> {
    return Promise.resolve()
  }

  getToken(): string | null {
    return this.token
  }

  login(returnTo: string): Promise<void> {
    this.loginCalls.push(returnTo)
    return this.loginFailure ? Promise.reject(this.loginFailure) : Promise.resolve()
  }

  completeLogin(): Promise<string> {
    return typeof this.callbackResult === 'string'
      ? Promise.resolve(this.callbackResult)
      : Promise.reject(this.callbackResult)
  }

  logout(): Promise<void> {
    this.logoutCalls += 1
    return Promise.resolve()
  }

  refresh(): Promise<boolean> {
    this.refreshCalls += 1
    return Promise.resolve(this.refreshResult)
  }
}

export function fakeAuthPort(token: string | null = 'test-token'): FakeAuthPort {
  return new FakeAuthPort(SIGNED_IN, token)
}

// Tipos de dominio derivados de los puertos: shared/ no puede importar features/.
type WorkspaceRepo = Ports['workspaces']
type MemberRepo = Ports['members']
type BoardRepo = Ports['boards']
type ListRepo = Ports['lists']
type CardRepo = Ports['cards']
export type Workspace = Awaited<ReturnType<WorkspaceRepo['create']>>
export type Member = Awaited<ReturnType<MemberRepo['invite']>>
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
    role: 'OWNER',
    ...overrides,
  }
}

export function aMember(overrides: Partial<Member> = {}): Member {
  return {
    id: nextId(),
    workspaceId: nextId() as Member['workspaceId'],
    userId: `user-${nextId().slice(-4)}`,
    role: 'VIEWER',
    version: 0,
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
  return { board: aBoard(), lastSeq: 0, lists: [], ...overrides }
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

/** Miembros en memoria (por workspace) con las reglas del backend: duplicado y ultimo OWNER. */
export class InMemoryMemberRepository extends FakeRepository implements MemberRepo {
  items: Member[]

  constructor(initial: Member[] = []) {
    super()
    this.items = [...initial]
  }

  list(workspaceId: Member['workspaceId']): Promise<Member[]> {
    return this.settle(() => this.items.filter((m) => m.workspaceId === workspaceId))
  }

  invite(workspaceId: Member['workspaceId'], userId: string, role: Member['role']) {
    return this.settle(() => {
      if (this.find(workspaceId, userId)) {
        throw new AppError('ya es miembro', 409, 'ALREADY_MEMBER')
      }
      const member = aMember({ workspaceId, userId, role })
      this.items.push(member)
      return member
    })
  }

  changeRole(workspaceId: Member['workspaceId'], userId: string, role: Member['role']) {
    return this.settle(() => {
      const found = this.require(workspaceId, userId)
      if (found.role === 'OWNER' && role !== 'OWNER' && this.owners(workspaceId) <= 1) {
        throw new AppError('ultimo owner', 409, 'LAST_OWNER')
      }
      const updated = { ...found, role, version: found.version + 1 }
      this.items = this.items.map((m) => (m === found ? updated : m))
      return updated
    })
  }

  remove(workspaceId: Member['workspaceId'], userId: string) {
    return this.settle(() => {
      const found = this.require(workspaceId, userId)
      if (found.role === 'OWNER' && this.owners(workspaceId) <= 1) {
        throw new AppError('ultimo owner', 409, 'LAST_OWNER')
      }
      this.items = this.items.filter((m) => m !== found)
    })
  }

  private owners(workspaceId: Member['workspaceId']): number {
    return this.items.filter((m) => m.workspaceId === workspaceId && m.role === 'OWNER').length
  }

  private find(workspaceId: Member['workspaceId'], userId: string): Member | undefined {
    return this.items.find((m) => m.workspaceId === workspaceId && m.userId === userId)
  }

  private require(workspaceId: Member['workspaceId'], userId: string): Member {
    const found = this.find(workspaceId, userId)
    if (!found) throw new AppError('no es miembro', 404, 'NOT_FOUND')
    return found
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
      this.boards.set(board.id, { board, lastSeq: 0, lists: [] })
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
      lastSeq: details.lastSeq,
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

// --- Tiempo real ---

type EventsPort = Ports['events']
export type BoardEnvelope = Parameters<Parameters<EventsPort['subscribe']>[1]>[0]
export type ConnectionStatus = Parameters<Parameters<EventsPort['subscribe']>[2]>[0]
type DistributiveOmit<T, K extends PropertyKey> = T extends unknown ? Omit<T, K> : never
/** Un tipo de evento con su payload, sin los metadatos del envelope. */
export type BoardEventBody = DistributiveOmit<
  BoardEnvelope,
  'boardId' | 'seq' | 'clientId' | 'occurredAt' | 'schemaVersion'
>

/** Envelope del contrato v1 a partir de un evento (por defecto sin clientId = escritura de otra pestana). */
export function anEnvelope(
  boardId: BoardEnvelope['boardId'],
  seq: number,
  event: BoardEventBody,
  clientId: string | null = null,
): BoardEnvelope {
  return {
    boardId,
    seq,
    clientId,
    occurredAt: '2026-01-01T00:00:00Z',
    schemaVersion: 1,
    ...event,
  } as BoardEnvelope
}

interface FakeSubscription {
  readonly boardId: BoardEnvelope['boardId']
  readonly onEvent: (envelope: BoardEnvelope) => void
  readonly onStatus: (status: ConnectionStatus) => void
  active: boolean
}

/**
 * Fake del puerto de eventos en tiempo real: el test emite envelopes y estados a mano y observa
 * suscripciones y consultas `since`.
 */
export class FakeBoardEvents implements EventsPort {
  readonly subscriptions: FakeSubscription[] = []
  readonly sinceCalls: Array<{ boardId: string; since: number }> = []
  /** Si true, tras suscribirse notifica `connected` inmediatamente (si no, solo `connecting`). */
  autoConnect = false
  /** `lastSeq` que devuelve `fetchEventsSince` (por defecto el propio `since`: no hay nada nuevo). */
  serverLastSeq?: number
  /** Si se define, `fetchEventsSince` rechaza con este error. */
  failSinceWith?: Error

  subscribe(
    boardId: FakeSubscription['boardId'],
    onEvent: FakeSubscription['onEvent'],
    onStatus: FakeSubscription['onStatus'],
  ): () => void {
    const sub: FakeSubscription = { boardId, onEvent, onStatus, active: true }
    this.subscriptions.push(sub)
    onStatus('connecting')
    if (this.autoConnect) onStatus('connected')
    return () => {
      sub.active = false
    }
  }

  fetchEventsSince(boardId: FakeSubscription['boardId'], since: number) {
    this.sinceCalls.push({ boardId, since })
    if (this.failSinceWith) return Promise.reject(this.failSinceWith)
    return Promise.resolve({ boardId, since, lastSeq: this.serverLastSeq ?? since, events: [] })
  }

  get activeSubscriptions(): FakeSubscription[] {
    return this.subscriptions.filter((s) => s.active)
  }

  /** Entrega un envelope a las suscripciones activas de su tablero. */
  emit(envelope: BoardEnvelope) {
    for (const s of this.activeSubscriptions)
      if (s.boardId === envelope.boardId) s.onEvent(envelope)
  }

  setStatus(boardId: FakeSubscription['boardId'], status: ConnectionStatus) {
    for (const s of this.activeSubscriptions) if (s.boardId === boardId) s.onStatus(status)
  }
}

/** Puertos fake en memoria; cada test puede sobreescribir los que necesite. */
export function fakePorts(overrides: Partial<Ports> = {}): Ports {
  return {
    auth: fakeAuthPort(),
    workspaces: new InMemoryWorkspaceRepository(),
    members: new InMemoryMemberRepository(),
    boards: new InMemoryBoardRepository(),
    lists: new InMemoryListRepository(),
    cards: new InMemoryCardRepository(),
    events: new FakeBoardEvents(),
    clientId: 'test-client',
    ...overrides,
  }
}
