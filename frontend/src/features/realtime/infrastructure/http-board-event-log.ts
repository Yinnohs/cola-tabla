import { boardId, type BoardId } from '../../../shared/kernel'
import { requireField, unwrap, type ApiClient } from '../../../shared/lib/api'
import type { BoardEventsPage } from '../application/ports/board-events-port'
import { parseEnvelope } from './parse-envelope'

/** `GET /api/boards/{id}/events?since=N`: log REST de eventos (misma forma de envelope que el WebSocket). */
export class HttpBoardEventLog {
  private readonly api: ApiClient

  constructor(api: ApiClient) {
    this.api = api
  }

  async fetchEventsSince(
    id: BoardId,
    since: number,
    signal?: AbortSignal,
  ): Promise<BoardEventsPage> {
    const dto = await unwrap(
      this.api.GET('/api/boards/{boardId}/events', {
        params: { path: { boardId: id }, query: { since } },
        signal,
      }),
    )
    return {
      boardId: boardId(requireField(dto.boardId, 'boardId')),
      since: requireField(dto.since, 'since'),
      lastSeq: requireField(dto.lastSeq, 'lastSeq'),
      events: (dto.events ?? []).map(parseEnvelope),
    }
  }
}
