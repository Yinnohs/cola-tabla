import { boardId, fractionalIndex, listId } from '../../../shared/kernel'
import { requireField } from '../../../shared/lib/api'
import type { List } from '../domain/list'
import type { ListDto } from './list-dto'

export function toList(dto: ListDto): List {
  return {
    id: listId(requireField(dto.id, 'id')),
    boardId: boardId(requireField(dto.boardId, 'boardId')),
    name: requireField(dto.name, 'name'),
    position: fractionalIndex(requireField(dto.position, 'position')),
    version: requireField(dto.version, 'version'),
  }
}
