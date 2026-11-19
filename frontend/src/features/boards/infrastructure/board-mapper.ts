import {
  boardId,
  cardId,
  fractionalIndex,
  listId,
  workspaceId,
  type ListId,
} from '../../../shared/kernel'
import { requireField } from '../../../shared/lib/api'
import type { Board } from '../domain/board'
import type { BoardCardView, BoardDetails, BoardListView } from '../domain/board-details'
import type { BoardCardDto, BoardDetailsDto, BoardDto, BoardListDto } from './board-dto'

export function toBoard(dto: Pick<BoardDto, 'id' | 'workspaceId' | 'name' | 'version'>): Board {
  return {
    id: boardId(requireField(dto.id, 'id')),
    workspaceId: workspaceId(requireField(dto.workspaceId, 'workspaceId')),
    name: requireField(dto.name, 'name'),
    version: requireField(dto.version, 'version'),
  }
}

function toCard(dto: BoardCardDto, parent: ListId): BoardCardView {
  return {
    id: cardId(requireField(dto.id, 'card.id')),
    listId: parent,
    title: requireField(dto.title, 'card.title'),
    description: dto.description ?? '',
    position: fractionalIndex(requireField(dto.position, 'card.position')),
    version: requireField(dto.version, 'card.version'),
  }
}

function toList(dto: BoardListDto): BoardListView {
  const id = listId(requireField(dto.id, 'list.id'))
  return {
    id,
    name: requireField(dto.name, 'list.name'),
    position: fractionalIndex(requireField(dto.position, 'list.position')),
    version: requireField(dto.version, 'list.version'),
    cards: (dto.cards ?? []).map((c) => toCard(c, id)),
  }
}

export function toBoardDetails(dto: BoardDetailsDto): BoardDetails {
  return {
    board: toBoard(dto),
    lastSeq: requireField(dto.lastSeq, 'lastSeq'),
    lists: (dto.lists ?? []).map(toList),
  }
}
