import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { TEST_API_URL, setupMswServer } from '../../../shared/testing/msw'
import { createApiClient } from '../../../shared/lib/api'
import { AppError } from '../../../shared/lib'
import { boardId } from '../../../shared/kernel'
import { HttpBoardEventLog } from './http-board-event-log'

const server = setupMswServer()
const log = new HttpBoardEventLog(createApiClient({ baseUrl: TEST_API_URL }))
const B = '6f1c1b0e-8a7d-4c43-9d57-2f0a3d1f0001'

describe('HttpBoardEventLog', () => {
  it('pide since por query y mapea los eventos al envelope de dominio', async () => {
    let since: string | null = null
    server.use(
      http.get(`${TEST_API_URL}/api/boards/${B}/events`, ({ request }) => {
        since = new URL(request.url).searchParams.get('since')
        return HttpResponse.json({
          boardId: B,
          since: 1,
          lastSeq: 3,
          events: [
            {
              boardId: B,
              seq: 2,
              type: 'card.deleted',
              payload: { cardId: 'c1', listId: 'l1' },
              occurredAt: '2026-01-01T00:00:00Z',
              schemaVersion: 1,
            },
          ],
        })
      }),
    )
    const page = await log.fetchEventsSince(boardId(B), 1)
    expect(since).toBe('1')
    expect(page).toMatchObject({ boardId: B, since: 1, lastSeq: 3 })
    expect(page.events).toHaveLength(1)
    expect(page.events[0]).toMatchObject({ seq: 2, type: 'card.deleted', clientId: null })
  })

  it('410 EVENTS_GONE y 400 INVALID_SINCE se propagan como AppError con su code', async () => {
    server.use(
      http.get(`${TEST_API_URL}/api/boards/${B}/events`, ({ request }) => {
        const since = new URL(request.url).searchParams.get('since')
        const [status, code] = since === '0' ? [410, 'EVENTS_GONE'] : [400, 'INVALID_SINCE']
        return HttpResponse.json(
          { title: 'x', status, code },
          { status, headers: { 'Content-Type': 'application/problem+json' } },
        )
      }),
    )
    const gone = await log.fetchEventsSince(boardId(B), 0).catch((e: unknown) => e)
    expect(gone).toBeInstanceOf(AppError)
    expect(gone).toMatchObject({ status: 410, code: 'EVENTS_GONE' })
    const bad = await log.fetchEventsSince(boardId(B), 99).catch((e: unknown) => e)
    expect(bad).toMatchObject({ status: 400, code: 'INVALID_SINCE' })
  })
})
