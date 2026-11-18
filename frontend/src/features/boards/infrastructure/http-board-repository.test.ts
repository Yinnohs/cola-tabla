import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../../shared/testing/msw'
import { createApiClient } from '../../../shared/lib/api'
import { StaleVersionError } from '../../../shared/lib'
import { boardId, workspaceId } from '../../../shared/kernel'
import { HttpBoardRepository } from './http-board-repository'

const server = setupMswServer()
const repo = new HttpBoardRepository(createApiClient({ baseUrl: TEST_API_URL }))

const B = '22222222-2222-4222-8222-222222222222'
const W = '11111111-1111-4111-8111-111111111111'
const boardDto = {
  id: B,
  workspaceId: W,
  name: 'Sprint',
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
}

const details = {
  id: B,
  workspaceId: W,
  name: 'Sprint',
  version: 3,
  lists: [
    {
      id: 'l-2',
      name: 'Done',
      position: 'k',
      version: 0,
      cards: [],
    },
    {
      id: 'l-1',
      name: 'Todo',
      position: 'V',
      version: 1,
      cards: [
        { id: 'c-1', title: 'Tarea', description: 'desc', position: 'V', version: 2 },
        { id: 'c-2', title: 'Sin descripcion', position: 'l', version: 0 },
      ],
    },
  ],
}

describe('HttpBoardRepository', () => {
  it('get mapea BoardDetailsResponse (listas y tarjetas anidadas)', async () => {
    server.use(http.get(`${TEST_API_URL}/api/boards/${B}`, () => HttpResponse.json(details)))
    const result = await repo.get(boardId(B))
    expect(result.board).toEqual({ id: B, workspaceId: W, name: 'Sprint', version: 3 })
    expect(result.lists).toHaveLength(2)
    const todo = result.lists.find((l) => l.name === 'Todo')
    expect(todo?.cards[0]).toEqual({
      id: 'c-1',
      listId: 'l-1',
      title: 'Tarea',
      description: 'desc',
      position: 'V',
      version: 2,
    })
    expect(todo?.cards[1]?.description).toBe('')
  })

  it('get con posicion invalida falla', async () => {
    const bad = { ...details, lists: [{ ...details.lists[0], position: 'V0' }] }
    server.use(http.get(`${TEST_API_URL}/api/boards/${B}`, () => HttpResponse.json(bad)))
    await expect(repo.get(boardId(B))).rejects.toThrow()
  })

  it('listByWorkspace mapea BoardResponse[] y conserva el orden', async () => {
    const second = { ...boardDto, id: '33333333-3333-4333-8333-333333333333', name: 'Backlog' }
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces/${W}/boards`, () =>
        HttpResponse.json([boardDto, second]),
      ),
    )
    const boards = await repo.listByWorkspace(workspaceId(W))
    expect(boards.map((b) => b.name)).toEqual(['Sprint', 'Backlog'])
    expect(boards[0]).toEqual({ id: B, workspaceId: W, name: 'Sprint', version: 0 })
  })

  it('listByWorkspace devuelve [] si no hay tableros', async () => {
    server.use(http.get(`${TEST_API_URL}/api/workspaces/${W}/boards`, () => HttpResponse.json([])))
    expect(await repo.listByWorkspace(workspaceId(W))).toEqual([])
  })

  it('listByWorkspace propaga el 404 como AppError', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces/${W}/boards`, () =>
        HttpResponse.json({ title: 'Not Found', status: 404, code: 'NOT_FOUND' }, { status: 404 }),
      ),
    )
    await expect(repo.listByWorkspace(workspaceId(W))).rejects.toMatchObject({ status: 404 })
  })

  it('create hace POST al workspace', async () => {
    let body: unknown
    server.use(
      http.post(`${TEST_API_URL}/api/workspaces/${W}/boards`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(boardDto, { status: 201 })
      }),
    )
    const board = await repo.create(workspaceId(W), 'Sprint')
    expect(body).toEqual({ name: 'Sprint' })
    expect(board).toEqual({ id: B, workspaceId: W, name: 'Sprint', version: 0 })
  })

  it('rename hace PATCH con name y version', async () => {
    let body: unknown
    server.use(
      http.patch(`${TEST_API_URL}/api/boards/${B}`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...boardDto, name: 'Nuevo', version: 1 })
      }),
    )
    const board = await repo.rename(boardId(B), 'Nuevo', 0)
    expect(body).toEqual({ name: 'Nuevo', version: 0 })
    expect(board).toMatchObject({ name: 'Nuevo', version: 1 })
  })

  it('rename con 409 lanza StaleVersionError', async () => {
    server.use(
      http.patch(`${TEST_API_URL}/api/boards/${B}`, () =>
        HttpResponse.json(
          { title: 'Conflict', status: 409, code: 'STALE_VERSION' },
          { status: 409 },
        ),
      ),
    )
    await expect(repo.rename(boardId(B), 'x', 0)).rejects.toBeInstanceOf(StaleVersionError)
  })
})
