import { describe, expect, it } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import {
  aBoard,
  aBoardDetails,
  aCardView,
  aListView,
  anEnvelope,
  createTestQueryClient,
  fakeBoardPorts,
  FakeBoardEvents,
  renderHookWithPorts,
  type BoardEnvelope,
} from '../../../shared/testing'
import { AppError } from '../../../shared/lib'
import { boardKeys, selectCardsOf, useBoard, type NormalizedBoard } from '../../boards'
import { useMoveCard } from '../../cards'
import { useBoardSync } from './use-board-sync'

const OWN = 'test-client' // clientId por defecto de fakePorts

function setup(initialSeq = 10) {
  const todo = aListView({ name: 'Todo', position: 'G' as never })
  const doing = aListView({ name: 'Doing', position: 'V' as never })
  const a = aCardView({ title: 'A', listId: todo.id, position: 'G' as never, version: 1 })
  const b = aCardView({ title: 'B', listId: todo.id, position: 'V' as never, version: 1 })
  const details = aBoardDetails({
    board: aBoard(),
    lastSeq: initialSeq,
    lists: [{ ...todo, cards: [a, b] }, doing],
  })
  const ports = fakeBoardPorts(details)
  const events = new FakeBoardEvents()
  // El "servidor": cada GET devuelve el estado de los repos con el lastSeq actual.
  const server = {
    lastSeq: initialSeq,
    gets: 0,
    failGet: false,
    seqByGet: undefined as ((n: number) => number) | undefined,
  }
  const originalGet = ports.boards.get.bind(ports.boards)
  ports.boards.get = async (id) => {
    server.gets += 1
    if (server.failGet) throw new Error('red')
    return { ...(await originalGet(id)), lastSeq: server.seqByGet?.(server.gets) ?? server.lastSeq }
  }
  const queryClient = createTestQueryClient()
  const boardId = details.board.id
  const cache = () => queryClient.getQueryData<NormalizedBoard>(boardKeys.detail(boardId))!
  const titles = (listId: typeof todo.id) => selectCardsOf(cache(), listId).map((c) => c.title)

  async function mount<R>(extra: () => R = () => undefined as R) {
    const utils = renderHookWithPorts(
      () => {
        useBoard(boardId)
        return { sync: useBoardSync(boardId), extra: extra() }
      },
      { ports: { ...ports, events }, queryClient },
    )
    await waitFor(() => expect(queryClient.getQueryData(boardKeys.detail(boardId))).toBeDefined())
    return utils
  }

  const moved = (
    seq: number,
    card = a,
    to = doing,
    clientId: string | null = null,
  ): BoardEnvelope =>
    anEnvelope(
      boardId,
      seq,
      {
        type: 'card.moved',
        payload: {
          card: {
            id: card.id,
            listId: to.id,
            title: card.title,
            description: '',
            position: 'M' as never,
            version: card.version + 1,
          },
          fromListId: card.listId,
        },
      },
      clientId,
    )

  return {
    todo,
    doing,
    a,
    b,
    ports,
    events,
    server,
    queryClient,
    boardId,
    cache,
    titles,
    mount,
    moved,
  }
}

describe('useBoardSync', () => {
  it('se suscribe al montar, informa el estado y se da de baja al desmontar', async () => {
    const s = setup()
    const { result, unmount } = await s.mount()
    expect(s.events.activeSubscriptions).toHaveLength(1)
    expect(result.current.sync.connection).toBe('connecting')

    act(() => s.events.setStatus(s.boardId, 'connected'))
    expect(result.current.sync.connection).toBe('connected')

    unmount()
    expect(s.events.activeSubscriptions).toHaveLength(0)
  })

  it('muestra access-denied cuando el servidor rechaza la suscripcion (sin reintentos por su parte)', async () => {
    const s = setup()
    const { result } = await s.mount()
    act(() => s.events.setStatus(s.boardId, 'access-denied'))
    expect(result.current.sync.connection).toBe('access-denied')
    expect(s.events.subscriptions).toHaveLength(1)
  })

  it('aplica un evento remoto en orden (seq == lastSeq + 1) y avanza lastSeq', async () => {
    const s = setup()
    await s.mount()

    act(() => s.events.emit(s.moved(11)))

    expect(s.titles(s.doing.id)).toEqual(['A'])
    expect(s.titles(s.todo.id)).toEqual(['B'])
    expect(s.cache().lastSeq).toBe(11)
    expect(s.server.gets).toBe(1) // solo la carga inicial
  })

  it('ignora el eco propio (clientId) pero avanza lastSeq', async () => {
    const s = setup()
    await s.mount()

    act(() => s.events.emit(s.moved(11, s.a, s.doing, OWN)))

    expect(s.titles(s.todo.id)).toEqual(['A', 'B']) // no se reaplico
    expect(s.cache().lastSeq).toBe(11)
    act(() => s.events.emit(s.moved(12, s.b, s.doing, 'otra-pestana')))
    expect(s.titles(s.doing.id)).toEqual(['B']) // el siguiente ajeno si se aplica
    expect(s.cache().lastSeq).toBe(12)
  })

  it('ignora duplicados y eventos ya incluidos en la carga', async () => {
    const s = setup()
    await s.mount()
    act(() => s.events.emit(s.moved(11)))
    const after = s.cache()

    act(() => s.events.emit(s.moved(11))) // duplicado
    act(() => s.events.emit(s.moved(7))) // anterior a la carga

    expect(s.cache()).toBe(after)
    expect(s.server.gets).toBe(1)
  })

  it('un hueco (10 -> 12) recarga el tablero completo y la cache queda con el lastSeq de la carga', async () => {
    const s = setup()
    await s.mount()
    s.ports.cards.editRemotely(s.a.id, { title: 'A remota' })
    s.server.lastSeq = 12

    act(() => s.events.emit(s.moved(12)))

    await waitFor(() => expect(s.server.gets).toBe(2))
    await waitFor(() => expect(s.cache().lastSeq).toBe(12))
    expect(s.titles(s.todo.id)).toContain('A remota') // viene del GET, no del evento perdido
    expect(s.titles(s.doing.id)).toEqual([]) // el evento del hueco no se aplico
  })

  it('un tipo de evento desconocido recarga en vez de ignorarse', async () => {
    const s = setup()
    await s.mount()
    s.server.lastSeq = 11

    act(() =>
      s.events.emit(
        anEnvelope(s.boardId, 11, { type: 'unknown', rawType: 'list.deleted', payload: {} }),
      ),
    )

    await waitFor(() => expect(s.server.gets).toBe(2))
    await waitFor(() => expect(s.cache().lastSeq).toBe(11))
  })

  it('ignora envelopes de otro tablero', async () => {
    const s = setup()
    await s.mount()
    act(() => s.events.emit({ ...s.moved(11), boardId: 'otro' as never }))
    // el fake solo entrega al tablero del envelope: no hay suscriptor, pero tampoco cambia nada
    expect(s.cache().lastSeq).toBe(10)
  })

  it('al conectar consulta el log; si el servidor ya va por delante, recarga', async () => {
    const s = setup()
    await s.mount()
    s.server.lastSeq = 12
    s.events.serverLastSeq = 12

    act(() => s.events.setStatus(s.boardId, 'connected'))

    await waitFor(() => expect(s.events.sinceCalls).toEqual([{ boardId: s.boardId, since: 10 }]))
    await waitFor(() => expect(s.cache().lastSeq).toBe(12))
    expect(s.server.gets).toBe(2)
  })

  it('al conectar sin novedades no recarga', async () => {
    const s = setup()
    await s.mount()
    act(() => s.events.setStatus(s.boardId, 'connected'))
    await waitFor(() => expect(s.events.sinceCalls).toHaveLength(1))
    expect(s.server.gets).toBe(1)
  })

  it('si el log responde 410/400/red, ante duda recarga', async () => {
    const s = setup()
    await s.mount()
    s.events.failSinceWith = new AppError('gone', 410, 'EVENTS_GONE')

    act(() => s.events.setStatus(s.boardId, 'connected'))

    await waitFor(() => expect(s.server.gets).toBe(2))
  })

  it('una recarga externa que deja la cache por detras de lo visto se corrige (vigilante de cache)', async () => {
    const s = setup()
    await s.mount()
    act(() => s.events.emit(s.moved(11)))
    expect(s.cache().lastSeq).toBe(11)

    // La 2a carga (p. ej. por foco de ventana) devuelve una instantanea anterior al evento 11;
    // la 3a (la que dispara el vigilante) ya lo incluye.
    s.server.seqByGet = (n) => (n === 2 ? 10 : 11)
    await act(() => s.queryClient.refetchQueries({ queryKey: boardKeys.detail(s.boardId) }))

    await waitFor(() => expect(s.cache().lastSeq).toBe(11))
    expect(s.server.gets).toBe(3)
  })

  it('los eventos recibidos durante una recarga no se pierden: se recarga hasta alcanzarlos', async () => {
    const s = setup()
    await s.mount()
    s.server.lastSeq = 12
    act(() => s.events.emit(s.moved(12))) // hueco -> recarga (ya en curso)
    s.server.lastSeq = 13
    act(() => s.events.emit(s.moved(13, s.b))) // llega durante la recarga

    await waitFor(() => expect(s.cache().lastSeq).toBe(13))
  })
})

describe('useBoardSync con mutaciones propias en vuelo', () => {
  it('un evento remoto mas nuevo no se pisa con la respuesta (mas antigua) de nuestra mutacion', async () => {
    const s = setup()
    const { result } = await s.mount(() => useMoveCard(s.boardId))
    const release = s.ports.cards.holdNext()

    // movemos A a "Doing" (optimista); la peticion queda retenida
    act(() => {
      result.current.extra.moveCard(s.a.id, s.doing.id, 0)
    })
    await waitFor(() => expect(s.titles(s.doing.id)).toEqual(['A']))

    // otra persona edita/mueve A despues (version 3 > la respuesta 2 de nuestro move)
    const remote = {
      ...s.moved(11),
      payload: {
        card: {
          id: s.a.id,
          listId: s.todo.id,
          title: 'A remota',
          description: '',
          position: 'Z' as never,
          version: 3,
        },
        fromListId: s.doing.id,
      },
    } as BoardEnvelope
    act(() => s.events.emit(remote))
    expect(s.cache().cards[s.a.id]?.version).toBe(3)

    await act(async () => {
      release()
    })
    await waitFor(() => expect(s.ports.cards.moves).toHaveLength(1))

    // la respuesta (version 2) NO retrocede el estado: queda la version 3 del evento remoto
    await waitFor(() => expect(s.cache().cards[s.a.id]?.version).toBe(3))
    expect(s.cache().cards[s.a.id]?.title).toBe('A remota')
  })

  it('el eco de nuestro propio move no se reaplica y lastSeq avanza', async () => {
    const s = setup()
    const { result } = await s.mount(() => useMoveCard(s.boardId))

    await act(async () => {
      result.current.extra.moveCard(s.a.id, s.doing.id, 0)
    })
    await waitFor(() => expect(s.ports.cards.moves).toHaveLength(1))
    const own = s.cache().cards[s.a.id]!
    act(() =>
      s.events.emit(
        anEnvelope(
          s.boardId,
          11,
          {
            type: 'card.moved',
            payload: { card: { ...own, position: 'Q' as never }, fromListId: s.todo.id },
          },
          OWN,
        ),
      ),
    )
    expect(s.cache().cards[s.a.id]?.position).toBe(own.position) // intacta
    expect(s.cache().lastSeq).toBe(11)
  })
})
