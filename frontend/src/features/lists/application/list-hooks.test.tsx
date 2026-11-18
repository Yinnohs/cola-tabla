import { describe, expect, it } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import {
  aBoard,
  aBoardDetails,
  aListView,
  createTestQueryClient,
  fakeBoardPorts,
  renderHookWithPorts,
} from '../../../shared/testing'
import { boardKeys, selectLists, useBoard, type NormalizedBoard } from '../../boards'
import { InvalidListNameError } from './errors'
import { useCreateList } from './use-create-list'
import { useMoveList } from './use-move-list'
import { useRenameList } from './use-rename-list'

function seeded() {
  const a = aListView({ name: 'A', position: 'G' as never })
  const b = aListView({ name: 'B', position: 'V' as never })
  const c = aListView({ name: 'C', position: 'k' as never })
  const details = aBoardDetails({ board: aBoard(), lists: [a, b, c] })
  const ports = fakeBoardPorts(details)
  const queryClient = createTestQueryClient()
  const boardId = details.board.id
  const cache = () => queryClient.getQueryData<NormalizedBoard>(boardKeys.detail(boardId))!
  const names = () => selectLists(cache()).map((l) => l.name)
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
  return { a, b, c, boardId, ports, cache, names, render }
}

describe('useCreateList', () => {
  it('crea la lista al final y la inserta en la cache', async () => {
    const s = seeded()
    const { result } = await s.render(() => useCreateList(s.boardId))
    await act(() => result.current.mutateAsync('  Done '))
    expect(s.names()).toEqual(['A', 'B', 'C', 'Done'])
  })

  it('rechaza nombre vacio', async () => {
    const s = seeded()
    const { result } = await s.render(() => useCreateList(s.boardId))
    const error = await act(() => result.current.mutateAsync('').catch((e: unknown) => e))
    expect(error).toBeInstanceOf(InvalidListNameError)
  })
})

describe('useRenameList', () => {
  it('renombra al instante y reconcilia version', async () => {
    const s = seeded()
    const { result } = await s.render(() => useRenameList(s.boardId))
    const release = s.ports.lists.holdNext()
    act(() => result.current.mutate({ listId: s.a.id, name: 'Nuevo' }))
    await waitFor(() => expect(s.names()[0]).toBe('Nuevo'))
    release()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(s.cache().lists[s.a.id]?.version).toBe(1)
  })

  it('rollback si falla', async () => {
    const s = seeded()
    const { result } = await s.render(() => useRenameList(s.boardId))
    s.ports.lists.failNextWith = new Error('boom')
    const release = s.ports.lists.holdNext()
    act(() => result.current.mutate({ listId: s.a.id, name: 'Nuevo' }))
    await waitFor(() => expect(s.names()[0]).toBe('Nuevo'))
    release()
    await waitFor(() => expect(result.current.isError).toBe(true))
    await waitFor(() => expect(s.names()).toEqual(['A', 'B', 'C']))
  })
})

describe('useMoveList', () => {
  it('reordena al instante y envia los ids de los vecinos', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveList(s.boardId))
    const release = s.ports.lists.holdNext()
    act(() => {
      result.current.moveList(s.c.id, 1) // C entre A y B
    })
    await waitFor(() => expect(s.names()).toEqual(['A', 'C', 'B']))
    release()
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(s.ports.lists.moves).toEqual([{ id: s.c.id, prevListId: s.a.id, nextListId: s.b.id }])
    expect(s.cache().lists[s.c.id]?.version).toBe(1)
    expect(s.names()).toEqual(['A', 'C', 'B'])
  })

  it('rollback si falla', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveList(s.boardId))
    s.ports.lists.failNextWith = new Error('boom')
    const release = s.ports.lists.holdNext()
    act(() => {
      result.current.moveList(s.a.id, 2)
    })
    await waitFor(() => expect(s.names()).toEqual(['B', 'C', 'A']))
    release()
    await waitFor(() => expect(result.current.isError).toBe(true))
    await waitFor(() => expect(s.names()).toEqual(['A', 'B', 'C']))
  })

  it('no hace nada si no cambia la posicion', async () => {
    const s = seeded()
    const { result } = await s.render(() => useMoveList(s.boardId))
    let moved = true
    act(() => {
      moved = result.current.moveList(s.b.id, 1)
    })
    expect(moved).toBe(false)
    expect(s.ports.lists.moves).toEqual([])
  })
})
