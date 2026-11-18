import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../testing/msw'
import { AppError, StaleVersionError } from '../errors'
import { createApiClient, unwrap } from './client'

const server = setupMswServer()

describe('api client', () => {
  it('inyecta Authorization Bearer (token sincrono o asincrono)', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces`, ({ request }) =>
        HttpResponse.json([{ name: request.headers.get('authorization') }]),
      ),
    )
    const api = createApiClient({ baseUrl: TEST_API_URL, getToken: async () => 'tok' })
    const data = await unwrap(api.GET('/api/workspaces'))
    expect(data).toEqual([{ name: 'Bearer tok' }])
  })

  it('sin token no manda Authorization', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces`, ({ request }) =>
        HttpResponse.json([{ name: String(request.headers.get('authorization')) }]),
      ),
    )
    const api = createApiClient({ baseUrl: TEST_API_URL })
    expect(await unwrap(api.GET('/api/workspaces'))).toEqual([{ name: 'null' }])
  })

  it('ProblemDetail 4xx -> AppError con code y status', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces`, () =>
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
    const api = createApiClient({ baseUrl: TEST_API_URL })
    const e = await unwrap(api.GET('/api/workspaces')).catch((x: unknown) => x)
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
      http.patch(`${TEST_API_URL}/api/boards/b1`, () =>
        HttpResponse.json(
          { title: 'Conflict', status: 409, code: 'STALE_VERSION' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const api = createApiClient({ baseUrl: TEST_API_URL })
    const e = await unwrap(
      api.PATCH('/api/boards/{boardId}', {
        params: { path: { boardId: 'b1' } },
        body: { name: 'x', version: 1 },
      }),
    ).catch((x: unknown) => x)
    expect(e).toBeInstanceOf(StaleVersionError)
    expect(e).toMatchObject({ status: 409, code: 'STALE_VERSION' })
  })

  it('error sin ProblemDetail -> AppError generico', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces`, () => new HttpResponse('boom', { status: 500 })),
    )
    const api = createApiClient({ baseUrl: TEST_API_URL })
    const e = await unwrap(api.GET('/api/workspaces')).catch((x: unknown) => x)
    expect(e).toMatchObject({ status: 500, code: 'HTTP_500' })
  })

  it('204 no lanza', async () => {
    server.use(
      http.delete(`${TEST_API_URL}/api/cards/c1`, () => new HttpResponse(null, { status: 204 })),
    )
    const api = createApiClient({ baseUrl: TEST_API_URL })
    await expect(
      unwrap(api.DELETE('/api/cards/{cardId}', { params: { path: { cardId: 'c1' } } })),
    ).resolves.toBeUndefined()
  })
})
