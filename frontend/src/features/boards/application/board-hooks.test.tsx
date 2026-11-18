import { describe, expect, it } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import {
  InMemoryBoardRepository,
  aBoard,
  aBoardDetails,
  aCardView,
  aListView,
  createTestQueryClient,
  renderHookWithPorts,
} from '../../../shared/testing'
import { workspaceId } from '../../../shared/kernel'
import { boardKeys } from './query-keys'
import { InvalidBoardNameError, useCreateBoard } from './use-create-board'
import { useBoard } from './use-board'
import { useBoards } from './use-boards'
import { useRenameBoard } from './use-rename-board'
import type { NormalizedBoard } from '../domain/normalized-board'

function seeded() {
  const list = aListView({ name: 'Todo' })
  const card = aCardView({ title: 'Tarea', listId: list.id })
  const details = aBoardDetails({
    board: aBoard({ name: 'Original' }),
    lists: [{ ...list, cards: [card] }],
  })
  return { details, list, card, repo: new InMemoryBoardRepository([details]) }
}

describe('useBoard', () => {
  it('carga el tablero y lo deja normalizado en la cache', async () => {
    const { details, list, card, repo } = seeded()
    const queryClient = createTestQueryClient()
    const { result } = renderHookWithPorts(() => useBoard(details.board.id), {
      ports: { boards: repo },
      queryClient,
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.lists[list.id]?.cardIds).toEqual([card.id])
    expect(result.current.data?.cards[card.id]?.title).toBe('Tarea')
    expect(queryClient.getQueryData(boardKeys.detail(details.board.id))).toBe(result.current.data)
  })
})

describe('useCreateBoard', () => {
  it('crea el tablero en el workspace', async () => {
    const repo = new InMemoryBoardRepository()
    const ws = workspaceId('w-1')
    const { result } = renderHookWithPorts(() => useCreateBoard(ws), { ports: { boards: repo } })
    const board = await act(() => result.current.mutateAsync('  Sprint  '))
    expect(board.name).toBe('Sprint')
    expect(board.workspaceId).toBe(ws)
    expect(repo.boards.has(board.id)).toBe(true)
  })

  it('rechaza nombre vacio sin llamar al puerto', async () => {
    const repo = new InMemoryBoardRepository()
    const { result } = renderHookWithPorts(() => useCreateBoard(workspaceId('w-1')), {
      ports: { boards: repo },
    })
    await act(async () => {
      await expect(result.current.mutateAsync(' ')).rejects.toBeInstanceOf(InvalidBoardNameError)
    })
    expect(repo.boards.size).toBe(0)
  })
})

describe('useRenameBoard', () => {
  it('renombra con la version cacheada y actualiza la cache', async () => {
    const { details, repo } = seeded()
    const { result } = renderHookWithPorts(
      () => ({ board: useBoard(details.board.id), rename: useRenameBoard(details.board.id) }),
      { ports: { boards: repo } },
    )
    await waitFor(() => expect(result.current.board.isSuccess).toBe(true))
    await act(() => result.current.rename.mutateAsync(' Nuevo '))
    await waitFor(() => expect(result.current.board.data?.board.name).toBe('Nuevo'))
    expect(result.current.board.data?.board.version).toBe(1)
  })

  it('ante conflicto refresca el tablero desde el servidor', async () => {
    const { details, repo } = seeded()
    const queryClient = createTestQueryClient()
    const { result } = renderHookWithPorts(
      () => ({ board: useBoard(details.board.id), rename: useRenameBoard(details.board.id) }),
      { ports: { boards: repo }, queryClient },
    )
    await waitFor(() => expect(result.current.board.isSuccess).toBe(true))
    // otro cliente renombra primero: el servidor sube a version 1
    await repo.rename(details.board.id, 'Remoto', 0)
    await act(async () => {
      await expect(result.current.rename.mutateAsync('Local')).rejects.toThrow('STALE_VERSION')
    })
    await waitFor(() => expect(result.current.board.data?.board.name).toBe('Remoto'))
    const cached = queryClient.getQueryData<NormalizedBoard>(boardKeys.detail(details.board.id))
    expect(cached?.board.version).toBe(1)
  })

  it('falla si el tablero no esta cargado', async () => {
    const { details, repo } = seeded()
    const { result } = renderHookWithPorts(() => useRenameBoard(details.board.id), {
      ports: { boards: repo },
    })
    await act(async () => {
      await expect(result.current.mutateAsync('x')).rejects.toThrow(/no esta cargado/)
    })
  })
})

describe('useBoards', () => {
  it('lista solo los tableros del workspace', async () => {
    const ws = workspaceId('w-1')
    const repo = new InMemoryBoardRepository([
      aBoardDetails({ board: aBoard({ workspaceId: ws, name: 'A' }) }),
      aBoardDetails({ board: aBoard({ workspaceId: workspaceId('w-2'), name: 'Ajeno' }) }),
    ])
    const { result } = renderHookWithPorts(() => useBoards(ws), { ports: { boards: repo } })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.map((b) => b.name)).toEqual(['A'])
  })

  it('crear un tablero invalida la lista y aparece de inmediato', async () => {
    const ws = workspaceId('w-1')
    const repo = new InMemoryBoardRepository()
    const { result } = renderHookWithPorts(
      () => ({ list: useBoards(ws), create: useCreateBoard(ws) }),
      { ports: { boards: repo }, queryClient: createTestQueryClient() },
    )
    await waitFor(() => expect(result.current.list.isSuccess).toBe(true))
    expect(result.current.list.data).toEqual([])
    await act(() => result.current.create.mutateAsync('Nuevo'))
    await waitFor(() => expect(result.current.list.data?.map((b) => b.name)).toEqual(['Nuevo']))
  })
})
