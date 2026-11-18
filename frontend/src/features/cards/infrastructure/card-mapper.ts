import { cardId, fractionalIndex, listId } from '../../../shared/kernel'
import { requireField } from '../../../shared/lib/api'
import type { Card } from '../domain/card'
import type { CardDto } from './card-dto'

export function toCard(dto: CardDto): Card {
  return {
    id: cardId(requireField(dto.id, 'id')),
    listId: listId(requireField(dto.listId, 'listId')),
    title: requireField(dto.title, 'title'),
    description: dto.description ?? '',
    position: fractionalIndex(requireField(dto.position, 'position')),
    version: requireField(dto.version, 'version'),
  }
}
