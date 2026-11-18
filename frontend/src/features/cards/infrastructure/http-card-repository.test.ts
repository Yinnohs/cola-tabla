import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../../shared/testing/msw'
import { createApiClient } from '../../../shared/lib/api'
import { AppError, StaleVersionError } from '../../../shared/lib'
import { cardId, listId } from '../../../shared/kernel'
import { HttpCardRepository } from './http-card-repository'

const server = setupMswServer()
const repo = new HttpCardRepository(createApiClient({ baseUrl: TEST_API_URL }))

const L = '33333333-3333-4333-8333-333333333333'
const L2 = '33333333-3333-4333-8333-444444444444'
const C = '44444444-4444-4444-8444-444444444444'
const P = '55555555-5555-4555-8555-555555555555'
const N = '66666666-6666-4666-8666-666666666666'

/** Forma real de CardResponse (docs/contracts/openapi.json). */
const cardDto = {
  id: C,
  listId: L,
  title: 'Tarea',
  description: 'detalle',
  position: 'V',
  version: 2,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('HttpCardRepository', () => {
  it('create: POST /api/lists/{id}/cards con title/description y mapea CardResponse', async () => {
    let body: unknown
    server.use(
      http.post(`${TEST_API_URL}/api/lists/${L}/cards`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(cardDto, { status: 201 })
      }),
    )
    const card = await repo.create(listId(L), 'Tarea', 'detalle')
    expect(body).toEqual({ title: 'Tarea', description: 'detalle' })
    expect(card).toEqual({
      id: C,
      listId: L,
      title: 'Tarea',
      description: 'detalle',
      position: 'V',
      version: 2,
    })
  })

  it('create: sin description en la respuesta -> cadena vacia', async () => {
    server.use(
      http.post(`${TEST_API_URL}/api/lists/${L}/cards`, () => {
        const { description: _d, ...rest } = cardDto
        void _d
        return HttpResponse.json(rest, { status: 201 })
      }),
    )
    expect((await repo.create(listId(L), 'Tarea')).description).toBe('')
  })

  it('update: PATCH con title, description y version', async () => {
    let body: unknown
    server.use(
      http.patch(`${TEST_API_URL}/api/cards/${C}`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...cardDto, version: 3 })
      }),
    )
    const card = await repo.update(cardId(C), { title: 'Nuevo', description: 'd', version: 2 })
    expect(body).toEqual({ title: 'Nuevo', description: 'd', version: 2 })
    expect(card.version).toBe(3)
  })

  it('update: 409 STALE_VERSION -> StaleVersionError', async () => {
    server.use(
      http.patch(`${TEST_API_URL}/api/cards/${C}`, () =>
        HttpResponse.json(
          { title: 'Conflict', status: 409, code: 'STALE_VERSION', detail: 'version vieja' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    await expect(
      repo.update(cardId(C), { title: 'x', description: '', version: 0 }),
    ).rejects.toBeInstanceOf(StaleVersionError)
  })

  it('move: envia listId + ids de vecinos (no una posicion) y mapea la respuesta', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.post(`${TEST_API_URL}/api/cards/${C}/move`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...cardDto, listId: L2, position: 'k', version: 3 })
      }),
    )
    const card = await repo.move(cardId(C), {
      listId: listId(L2),
      prevCardId: cardId(P),
      nextCardId: cardId(N),
    })
    expect(body).toEqual({ listId: L2, prevCardId: P, nextCardId: N })
    expect(body).not.toHaveProperty('position')
    expect(card).toMatchObject({ listId: L2, position: 'k', version: 3 })
  })

  it('move: vecinos null se omiten del cuerpo', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.post(`${TEST_API_URL}/api/cards/${C}/move`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(cardDto)
      }),
    )
    await repo.move(cardId(C), { listId: listId(L), prevCardId: null, nextCardId: null })
    expect(body).toEqual({ listId: L })
  })

  it('delete: 204 sin cuerpo', async () => {
    let called = false
    server.use(
      http.delete(`${TEST_API_URL}/api/cards/${C}`, () => {
        called = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await expect(repo.delete(cardId(C))).resolves.toBeUndefined()
    expect(called).toBe(true)
  })

  it('404 -> AppError con status y code', async () => {
    server.use(
      http.delete(`${TEST_API_URL}/api/cards/${C}`, () =>
        HttpResponse.json(
          { status: 404, code: 'NOT_FOUND', detail: 'no existe' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const error = await repo.delete(cardId(C)).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(AppError)
    expect(error).toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('respuesta malformada (sin position) -> error', async () => {
    server.use(
      http.post(`${TEST_API_URL}/api/lists/${L}/cards`, () => {
        const { position: _p, ...rest } = cardDto
        void _p
        return HttpResponse.json(rest, { status: 201 })
      }),
    )
    await expect(repo.create(listId(L), 'x')).rejects.toThrow(/position/)
  })
})
