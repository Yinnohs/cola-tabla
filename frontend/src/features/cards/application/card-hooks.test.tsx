import { describe, expect, it } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import {
  aBoard,
  aBoardDetails,
  aCardView,
  aListView,
  createTestQueryClient,
  fakeBoardPorts,
  renderHookWithPorts,
} from '../../../shared/testing'
import { boardKeys, selectCardsOf, useBoard, type NormalizedBoard } from '../../boards'
import { CardConflictError, InvalidCardTitleError } from './errors'
import { useCreateCard } from './use-create-card'
import { useDeleteCard } from './use-delete-card'
import { useMoveCard } from './use-move-card'
import { useUpdateCard } from './use-update-card'

function seeded() {
  const todo = aListView({ name: 'Todo', position: 'G' as never })
  const doing = aListView({ name: 'Doing', position: 'V' as never })
  const a = aCardView({ title: 'A', listId: todo.id, position: 'G' as never })
  const b = aCardView({ title: 'B', listId: todo.id, position: 'V' as never })
  const c = aCardView({ title: 'C', listId: todo.id, position: 'k' as never })
  const details = aBoardDetails({
    board: aBoard(),
    lists: [{ ...todo, cards: [a, b, c] }, doing],
  })
  const ports = fakeBoardPorts(details)
  const queryClient = createTestQueryClient()
  const boardId = details.board.id
  const cache = () => queryClient.getQueryData<NormalizedBoard>(boardKeys.detail(boardId))!
  const ids = (listId: typeof todo.id) => selectCardsOf(cache(), listId).map((x) => x.title)
  async function render<R>(hook: () => R) {
    const utils = renderHookWithPorts(
      () => {
        useBoard(boardId)
        return hook()
      },
      { ports, queryClient },
    )
    await waitFor(() => expect(queryClient.getQueryData(boardKeys.detail(boardId))).toBeDefined())
    return utils
  }
  return { todo, doing, a, b, c, boardId, ports, queryClient, cache, ids, render }
}

describe('useMoveCard', () => {
  it('aplica el movimiento al instante (optimista) y envia solo los ids de los vecinos', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveCard(s.boardId))
    const release = s.ports.cards.holdNext()

    act(() => {
      result.current.moveCard(s.c.id, s.todo.id, 1) // C entre A y B
    })

    // el servidor aun no ha respondido y la cache ya esta ordenada
    await waitFor(() => expect(s.ids(s.todo.id)).toEqual(['A', 'C', 'B']))
    expect(s.ports.cards.moves).toEqual([]) // sigue retenida
    release()
    await waitFor(() => expect(s.ports.cards.moves).toHaveLength(1))
    expect(s.ports.cards.moves[0]).toEqual({
      id: s.c.id,
      listId: s.todo.id,
      prevCardId: s.a.id,
      nextCardId: s.b.id,
    })
  })

  it('reconcilia con la posicion y version que devuelve el servidor', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveCard(s.boardId))
    act(() => {
      result.current.moveCard(s.a.id, s.todo.id, 2) // A al final
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    const server = s.ports.cards.items.find((c) => c.id === s.a.id)!
    expect(s.cache().cards[s.a.id]?.position).toBe(server.position)
    expect(s.cache().cards[s.a.id]?.version).toBe(1)
    expect(s.ids(s.todo.id)).toEqual(['B', 'C', 'A'])
  })

  it('mueve entre listas (a una lista vacia, sin vecinos)', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveCard(s.boardId))
    act(() => {
      result.current.moveCard(s.b.id, s.doing.id, 0)
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(s.ports.cards.moves[0]).toEqual({
      id: s.b.id,
      listId: s.doing.id,
      prevCardId: null,
      nextCardId: null,
    })
    expect(s.ids(s.todo.id)).toEqual(['A', 'C'])
    expect(s.ids(s.doing.id)).toEqual(['B'])
  })

  it('hace rollback si el servidor rechaza el movimiento', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveCard(s.boardId))
    s.ports.cards.failNextWith = new Error('boom')
    const release = s.ports.cards.holdNext()
    act(() => {
      result.current.moveCard(s.c.id, s.doing.id, 0)
    })
    await waitFor(() => expect(s.ids(s.doing.id)).toEqual(['C'])) // optimista
    release()
    await waitFor(() => expect(result.current.isError).toBe(true))
    await waitFor(() => expect(s.ids(s.todo.id)).toEqual(['A', 'B', 'C']))
    expect(s.ids(s.doing.id)).toEqual([])
    expect(s.cache().cards[s.c.id]?.listId).toBe(s.todo.id)
  })

  it('no llama al servidor si se suelta donde ya estaba', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveCard(s.boardId))
    let plan: unknown = 'sin-llamar'
    act(() => {
      plan = result.current.moveCard(s.b.id, s.todo.id, 1)
    })
    expect(plan).toBeNull()
    expect(s.ports.cards.moves).toEqual([])
  })
})

describe('useUpdateCard', () => {
  it('envia la version cacheada y actualiza la cache con la respuesta', async () => {
    const s = seeded()
    const { result } = await s.render(() => useUpdateCard(s.boardId))
    await act(() =>
      result.current.mutateAsync({ cardId: s.a.id, title: '  A2 ', description: 'desc' }),
    )
    expect(s.cache().cards[s.a.id]).toMatchObject({ title: 'A2', description: 'desc', version: 1 })
  })

  it('STALE_VERSION: refresca el tablero y lanza un error tipado de conflicto', async () => {
    const s = seeded()
    const { result } = await s.render(() => useUpdateCard(s.boardId))
    s.ports.cards.editRemotely(s.a.id, { title: 'Editada por otro' })
    let error: unknown
    await act(async () => {
      error = await result.current
        .mutateAsync({ cardId: s.a.id, title: 'Mi cambio', description: '' })
        .catch((e: unknown) => e)
    })
    expect(error).toBeInstanceOf(CardConflictError)
    // refetch: la cache tiene la version vigente del servidor
    await waitFor(() => expect(s.cache().cards[s.a.id]?.title).toBe('Editada por otro'))
    expect(s.cache().cards[s.a.id]?.version).toBe(1)
  })

  it('usa expectedVersion (la del formulario) en lugar de la cache', async () => {
    const s = seeded()
    const { result } = await s.render(() => useUpdateCard(s.boardId))
    s.ports.cards.editRemotely(s.a.id, { title: 'Remoto' })
    await s.queryClient.invalidateQueries({ queryKey: boardKeys.detail(s.boardId) })
    await waitFor(() => expect(s.cache().cards[s.a.id]?.version).toBe(1))
    const error = await act(() =>
      result.current
        .mutateAsync({ cardId: s.a.id, title: 'Mio', description: '', expectedVersion: 0 })
        .catch((e: unknown) => e),
    )
    expect(error).toBeInstanceOf(CardConflictError)
  })

  it('valida el titulo antes de llamar al servidor', async () => {
    const s = seeded()
    const { result } = await s.render(() => useUpdateCard(s.boardId))
    const error = await act(() =>
      result.current
        .mutateAsync({ cardId: s.a.id, title: '  ', description: '' })
        .catch((e: unknown) => e),
    )
    expect(error).toBeInstanceOf(InvalidCardTitleError)
  })
})

describe('useCreateCard', () => {
  it('crea la tarjeta y la inserta al final de la lista en la cache', async () => {
    const s = seeded()
    const { result } = await s.render(() => useCreateCard(s.boardId))
    await act(() => result.current.mutateAsync({ listId: s.todo.id, title: ' Nueva ' }))
    expect(s.ids(s.todo.id)).toEqual(['A', 'B', 'C', 'Nueva'])
  })

  it('rechaza titulo vacio', async () => {
    const s = seeded()
    const { result } = await s.render(() => useCreateCard(s.boardId))
    const error = await act(() =>
      result.current.mutateAsync({ listId: s.todo.id, title: '' }).catch((e: unknown) => e),
    )
    expect(error).toBeInstanceOf(InvalidCardTitleError)
  })
})

describe('useDeleteCard', () => {
  it('desaparece al instante y se borra en el servidor', async () => {
    const s = seeded()
    const { result } = await s.render(() => useDeleteCard(s.boardId))
    act(() => result.current.mutate(s.b.id))
    await waitFor(() => expect(s.ids(s.todo.id)).toEqual(['A', 'C']))
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(s.ports.cards.items.some((c) => c.id === s.b.id)).toBe(false)
  })

  it('restaura la tarjeta si el borrado falla', async () => {
    const s = seeded()
    const { result } = await s.render(() => useDeleteCard(s.boardId))
    s.ports.cards.failNextWith = new Error('boom')
    act(() => result.current.mutate(s.b.id))
    await waitFor(() => expect(result.current.isError).toBe(true))
    await waitFor(() => expect(s.ids(s.todo.id)).toEqual(['A', 'B', 'C']))
  })
})
