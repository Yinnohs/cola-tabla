import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../../shared/testing/msw'
import { createApiClient } from '../../../shared/lib/api'
import { AppError } from '../../../shared/lib'
import { workspaceId } from '../../../shared/kernel'
import { HttpWorkspaceRepository } from './http-workspace-repository'

const server = setupMswServer()
const repo = new HttpWorkspaceRepository(createApiClient({ baseUrl: TEST_API_URL }))

const dto = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Equipo',
  version: 0,
  createdAt: '2026-01-01T10:00:00Z',
  role: 'EDITOR',
}

describe('HttpWorkspaceRepository', () => {
  it('list mapea WorkspaceResponse[] a dominio', async () => {
    server.use(http.get(`${TEST_API_URL}/api/workspaces`, () => HttpResponse.json([dto])))
    expect(await repo.list()).toEqual([dto])
  })

  it('get hace GET por id y mapea a dominio', async () => {
    server.use(http.get(`${TEST_API_URL}/api/workspaces/${dto.id}`, () => HttpResponse.json(dto)))
    expect(await repo.get(workspaceId(dto.id))).toEqual(dto)
  })

  it('get de un workspace inexistente falla con AppError 404', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/workspaces/${dto.id}`, () =>
        HttpResponse.json({ title: 'Not Found', status: 404, code: 'NOT_FOUND' }, { status: 404 }),
      ),
    )
    const e = await repo.get(workspaceId(dto.id)).catch((x: unknown) => x)
    expect(e).toBeInstanceOf(AppError)
    expect(e).toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('create hace POST con el nombre y mapea el 201', async () => {
    let body: unknown
    server.use(
      http.post(`${TEST_API_URL}/api/workspaces`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(dto, { status: 201 })
      }),
    )
    const created = await repo.create('Equipo')
    expect(body).toEqual({ name: 'Equipo' })
    expect(created.name).toBe('Equipo')
  })

  it('respuesta sin campos obligatorios falla', async () => {
    server.use(http.get(`${TEST_API_URL}/api/workspaces`, () => HttpResponse.json([{ name: 'x' }])))
    await expect(repo.list()).rejects.toThrow(/malformada/)
  })

  it('propaga ProblemDetail como AppError con code', async () => {
    server.use(
      http.post(`${TEST_API_URL}/api/workspaces`, () =>
        HttpResponse.json(
          { title: 'Bad Request', status: 400, code: 'VALIDATION_ERROR' },
          { status: 400 },
        ),
      ),
    )
    const e = await repo.create('').catch((x: unknown) => x)
    expect(e).toBeInstanceOf(AppError)
    expect(e).toMatchObject({ status: 400, code: 'VALIDATION_ERROR' })
  })
})
