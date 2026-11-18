import type { CardId, ListId } from '../../../shared/kernel'
import { unwrap, type ApiClient } from '../../../shared/lib/api'
import type {
  CardMoveRequest,
  CardRepository,
  CardUpdate,
} from '../application/ports/card-repository'
import { toCard } from './card-mapper'

export class HttpCardRepository implements CardRepository {
  private readonly api: ApiClient

  constructor(api: ApiClient) {
    this.api = api
  }

  async create(listId: ListId, title: string, description?: string) {
    const dto = await unwrap(
      this.api.POST('/api/lists/{listId}/cards', {
        params: { path: { listId } },
        body: { title, description },
      }),
    )
    return toCard(dto)
  }

  async update(id: CardId, { title, description, version }: CardUpdate) {
    const dto = await unwrap(
      this.api.PATCH('/api/cards/{cardId}', {
        params: { path: { cardId: id } },
        body: { title, description, version },
      }),
    )
    return toCard(dto)
  }

  async move(id: CardId, { listId, prevCardId, nextCardId }: CardMoveRequest) {
    const dto = await unwrap(
      this.api.POST('/api/cards/{cardId}/move', {
        params: { path: { cardId: id } },
        body: {
          listId,
          prevCardId: prevCardId ?? undefined,
          nextCardId: nextCardId ?? undefined,
        },
      }),
    )
    return toCard(dto)
  }

  async delete(id: CardId) {
    await unwrap(this.api.DELETE('/api/cards/{cardId}', { params: { path: { cardId: id } } }))
  }
}
