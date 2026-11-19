import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../../shared/testing/msw'
import { createApiClient } from '../../../shared/lib/api'
import { AppError } from '../../../shared/lib'
import { workspaceId } from '../../../shared/kernel'
import { HttpMemberRepository } from './http-member-repository'

const server = setupMswServer()
const repo = new HttpMemberRepository(
  createApiClient({ baseUrl: TEST_API_URL, getToken: () => 'tok' }),
)
const ws = workspaceId('11111111-1111-4111-8111-111111111111')
const dto = {
  id: '22222222-2222-4222-8222-222222222222',
  workspaceId: ws,
  userId: 'kc-sub-1',
  role: 'EDITOR',
  version: 3,
}
const base = `${TEST_API_URL}/api/workspaces/${ws}/members`

describe('HttpMemberRepository', () => {
  it('list hace GET con bearer y mapea MemberResponse[] a dominio', async () => {
    let auth: string | null = null
    server.use(
      http.get(base, ({ request }) => {
        auth = request.headers.get('authorization')
        return HttpResponse.json([dto])
      }),
    )
    expect(await repo.list(ws)).toEqual([dto])
    expect(auth).toBe('Bearer tok')
  })

  it('invite hace POST con userId y rol', async () => {
    let body: unknown
    server.use(
      http.post(base, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(dto, { status: 201 })
      }),
    )
    const created = await repo.invite(ws, 'kc-sub-1', 'EDITOR')
    expect(body).toEqual({ userId: 'kc-sub-1', role: 'EDITOR' })
    expect(created.role).toBe('EDITOR')
  })

  it('changeRole hace PATCH sobre el userId y mapea la respuesta', async () => {
    let body: unknown
    server.use(
      http.patch(`${base}/kc-sub-1`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...dto, role: 'VIEWER', version: 4 })
      }),
    )
    const updated = await repo.changeRole(ws, 'kc-sub-1', 'VIEWER')
    expect(body).toEqual({ role: 'VIEWER' })
    expect(updated).toMatchObject({ role: 'VIEWER', version: 4 })
  })

  it('remove hace DELETE y acepta el 204', async () => {
    let called = false
    server.use(
      http.delete(`${base}/kc-sub-1`, () => {
        called = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await expect(repo.remove(ws, 'kc-sub-1')).resolves.toBeUndefined()
    expect(called).toBe(true)
  })

  it('LAST_OWNER (409) llega como AppError con su code, no como conflicto de version', async () => {
    server.use(
      http.delete(`${base}/kc-sub-1`, () =>
        HttpResponse.json({ status: 409, code: 'LAST_OWNER' }, { status: 409 }),
      ),
    )
    const e = await repo.remove(ws, 'kc-sub-1').catch((x: unknown) => x)
    expect(e).toBeInstanceOf(AppError)
    expect(e).toMatchObject({ status: 409, code: 'LAST_OWNER' })
  })

  it('403 para quien no es OWNER', async () => {
    server.use(
      http.get(base, () => HttpResponse.json({ status: 403, code: 'FORBIDDEN' }, { status: 403 })),
    )
    await expect(repo.list(ws)).rejects.toMatchObject({ status: 403 })
  })

  it('un rol desconocido o campos ausentes se rechazan', async () => {
    server.use(http.get(base, () => HttpResponse.json([{ ...dto, role: 'BOSS' }])))
    await expect(repo.list(ws)).rejects.toThrow(/Rol desconocido/)
    server.use(http.get(base, () => HttpResponse.json([{ userId: 'x' }])))
    await expect(repo.list(ws)).rejects.toThrow(/malformada/)
  })
})
