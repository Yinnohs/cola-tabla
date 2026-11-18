import { describe, expect, it } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import {
  InMemoryWorkspaceRepository,
  aWorkspace,
  createTestQueryClient,
  renderHookWithPorts,
} from '../../../shared/testing'
import { InvalidWorkspaceNameError, useCreateWorkspace } from './use-create-workspace'
import { useWorkspace } from './use-workspace'
import { useWorkspaces } from './use-workspaces'

describe('useWorkspaces', () => {
  it('lista los workspaces del puerto', async () => {
    const repo = new InMemoryWorkspaceRepository([
      aWorkspace({ name: 'A' }),
      aWorkspace({ name: 'B' }),
    ])
    const { result } = renderHookWithPorts(() => useWorkspaces(), { ports: { workspaces: repo } })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.map((w) => w.name)).toEqual(['A', 'B'])
  })

  it('expone el error del puerto', async () => {
    const repo = new InMemoryWorkspaceRepository()
    repo.failNextWith = new Error('boom')
    const { result } = renderHookWithPorts(() => useWorkspaces(), { ports: { workspaces: repo } })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error?.message).toBe('boom')
  })
})

describe('useCreateWorkspace', () => {
  it('crea (con nombre recortado) y refresca la lista', async () => {
    const repo = new InMemoryWorkspaceRepository()
    const { result } = renderHookWithPorts(
      () => ({ list: useWorkspaces(), create: useCreateWorkspace() }),
      { ports: { workspaces: repo }, queryClient: createTestQueryClient() },
    )
    await waitFor(() => expect(result.current.list.isSuccess).toBe(true))
    expect(result.current.list.data).toEqual([])

    await act(() => result.current.create.mutateAsync('  Nuevo  '))
    await waitFor(() => expect(result.current.list.data?.map((w) => w.name)).toEqual(['Nuevo']))
  })

  it('valida el nombre antes de llamar al puerto', async () => {
    const repo = new InMemoryWorkspaceRepository()
    const { result } = renderHookWithPorts(() => useCreateWorkspace(), {
      ports: { workspaces: repo },
    })
    await act(async () => {
      await expect(result.current.mutateAsync('   ')).rejects.toBeInstanceOf(
        InvalidWorkspaceNameError,
      )
    })
    expect(repo.items).toHaveLength(0)
  })
})

describe('useWorkspace', () => {
  it('carga un workspace por id', async () => {
    const ws = aWorkspace({ name: 'Equipo' })
    const repo = new InMemoryWorkspaceRepository([ws])
    const { result } = renderHookWithPorts(() => useWorkspace(ws.id), {
      ports: { workspaces: repo },
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.name).toBe('Equipo')
  })

  it('expone el 404 como AppError', async () => {
    const repo = new InMemoryWorkspaceRepository()
    const missing = aWorkspace().id
    const { result } = renderHookWithPorts(() => useWorkspace(missing), {
      ports: { workspaces: repo },
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toMatchObject({ status: 404 })
  })
})
