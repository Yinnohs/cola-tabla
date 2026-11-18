import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../../shared/testing/msw'
import { createApiClient } from '../../../shared/lib/api'
import { StaleVersionError } from '../../../shared/lib'
import { boardId, listId } from '../../../shared/kernel'
import { HttpListRepository } from './http-list-repository'

const server = setupMswServer()
const repo = new HttpListRepository(createApiClient({ baseUrl: TEST_API_URL }))

const B = '22222222-2222-4222-8222-222222222222'
const L = '33333333-3333-4333-8333-333333333333'
const P = '55555555-5555-4555-8555-555555555555'
const N = '66666666-6666-4666-8666-666666666666'

/** Forma real de ListResponse (docs/contracts/openapi.json). */
const listDto = {
  id: L,
  boardId: B,
  name: 'Todo',
  position: 'V',
  version: 0,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('HttpListRepository', () => {
  it('create: POST /api/boards/{id}/lists y mapea ListResponse', async () => {
    let body: unknown
    server.use(
      http.post(`${TEST_API_URL}/api/boards/${B}/lists`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(listDto, { status: 201 })
      }),
    )
    const list = await repo.create(boardId(B), 'Todo')
    expect(body).toEqual({ name: 'Todo' })
    expect(list).toEqual({ id: L, boardId: B, name: 'Todo', position: 'V', version: 0 })
  })

  it('rename: PATCH con name y version', async () => {
    let body: unknown
    server.use(
      http.patch(`${TEST_API_URL}/api/lists/${L}`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...listDto, name: 'Hecho', version: 1 })
      }),
    )
    const list = await repo.rename(listId(L), 'Hecho', 0)
    expect(body).toEqual({ name: 'Hecho', version: 0 })
    expect(list).toMatchObject({ name: 'Hecho', version: 1 })
  })

  it('rename: 409 -> StaleVersionError', async () => {
    server.use(
      http.patch(`${TEST_API_URL}/api/lists/${L}`, () =>
        HttpResponse.json(
          { status: 409, code: 'STALE_VERSION' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    await expect(repo.rename(listId(L), 'x', 0)).rejects.toBeInstanceOf(StaleVersionError)
  })

  it('move: envia ids de vecinos y devuelve la posicion calculada por el servidor', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.post(`${TEST_API_URL}/api/lists/${L}/move`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...listDto, position: 'k', version: 1 })
      }),
    )
    const list = await repo.move(listId(L), { prevListId: listId(P), nextListId: listId(N) })
    expect(body).toEqual({ prevListId: P, nextListId: N })
    expect(list).toMatchObject({ position: 'k', version: 1 })
  })

  it('move: vecinos null se omiten', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.post(`${TEST_API_URL}/api/lists/${L}/move`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(listDto)
      }),
    )
    await repo.move(listId(L), { prevListId: null, nextListId: listId(N) })
    expect(body).toEqual({ nextListId: N })
  })
})
