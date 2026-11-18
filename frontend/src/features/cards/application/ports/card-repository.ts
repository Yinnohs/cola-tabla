import type { CardId, ListId } from '../../../../shared/kernel'
import type { Card } from '../../domain/card'

export interface CardMoveRequest {
  readonly listId: ListId
  readonly prevCardId: CardId | null
  readonly nextCardId: CardId | null
}

export interface CardUpdate {
  readonly title: string
  readonly description: string
  readonly version: number
}

export interface CardRepository {
  create(listId: ListId, title: string, description?: string): Promise<Card>
  /** Lanza `StaleVersionError` (409) si `version` ya no es la vigente. */
  update(id: CardId, update: CardUpdate): Promise<Card>
  /** Se envian los vecinos, no una posicion: el servidor la calcula y devuelve position/version nuevos. */
  move(id: CardId, request: CardMoveRequest): Promise<Card>
  delete(id: CardId): Promise<void>
}
