import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { createHttpClient } from './http-client'
import { AppError, StaleVersionError } from './errors'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

const base = 'http://api.test'
const client = createHttpClient({ baseUrl: base, getToken: () => 'tok123' })

describe('http-client', () => {
  it('añade Authorization Bearer y parsea JSON', async () => {
    server.use(
      http.get(`${base}/ping`, ({ request }) =>
        HttpResponse.json({ auth: request.headers.get('authorization') }),
      ),
    )
    expect(await client.get('/ping')).toEqual({ auth: 'Bearer tok123' })
  })

  it('sin token no manda Authorization', async () => {
    server.use(
      http.get(`${base}/ping`, ({ request }) =>
        HttpResponse.json({ auth: request.headers.get('authorization') }),
      ),
    )
    const anon = createHttpClient({ baseUrl: base })
    expect(await anon.get('/ping')).toEqual({ auth: null })
  })

  it('envía body JSON y query params', async () => {
    server.use(
      http.post(`${base}/items`, async ({ request }) =>
        HttpResponse.json({ body: await request.json(), q: new URL(request.url).search }),
      ),
    )
    expect(await client.post('/items', { a: 1 }, { query: { x: 2, y: undefined } })).toEqual({
      body: { a: 1 },
      q: '?x=2',
    })
  })

  it('204 devuelve undefined', async () => {
    server.use(http.delete(`${base}/x`, () => new HttpResponse(null, { status: 204 })))
    expect(await client.delete('/x')).toBeUndefined()
  })

  it('ProblemDetail -> AppError con code y status', async () => {
    server.use(
      http.get(`${base}/forbidden`, () =>
        HttpResponse.json(
          {
            title: 'Forbidden',
            detail: 'No eres miembro',
            status: 403,
            code: 'FORBIDDEN_WORKSPACE',
          },
          { status: 403, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const e = await client.get('/forbidden').catch((x: unknown) => x)
    expect(e).toBeInstanceOf(AppError)
    expect(e).not.toBeInstanceOf(StaleVersionError)
    expect(e).toMatchObject({
      status: 403,
      code: 'FORBIDDEN_WORKSPACE',
      message: 'No eres miembro',
    })
  })

  it('409 STALE_VERSION -> StaleVersionError', async () => {
    server.use(
      http.put(`${base}/cards/1`, () =>
        HttpResponse.json(
          { title: 'Conflict', status: 409, code: 'STALE_VERSION' },
          { status: 409 },
        ),
      ),
    )
    const e = await client.put('/cards/1', { v: 1 }).catch((x: unknown) => x)
    expect(e).toBeInstanceOf(StaleVersionError)
    expect(e).toMatchObject({ status: 409, code: 'STALE_VERSION' })
  })

  it('error sin cuerpo JSON -> AppError genérico', async () => {
    server.use(http.get(`${base}/boom`, () => new HttpResponse('oops', { status: 500 })))
    const e = await client.get('/boom').catch((x: unknown) => x)
    expect(e).toBeInstanceOf(AppError)
    expect(e).toMatchObject({ status: 500, code: 'HTTP_500' })
  })
})
