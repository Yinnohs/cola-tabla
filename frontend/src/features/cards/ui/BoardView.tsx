import { useCallback, useState } from 'react'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import type { BoardId, CardId } from '../../../shared/kernel'
import { selectCardsOf, type NormalizedBoard } from '../../boards'
import { CreateListForm, ListView } from '../../lists'
import { CardDialog } from './CardDialog'
import { CreateCardForm } from './CreateCardForm'
import { BoardDnd } from './dnd/BoardDnd'
import { SortableCard } from './dnd/SortableCard'
import { SortableList } from './dnd/SortableList'

interface BoardViewProps {
  boardId: BoardId
  board: NormalizedBoard
}

/** Tablero completo: listas reordenables con tarjetas arrastrables, formularios de alta y dialogo de edicion. */
export function BoardView({ boardId, board }: BoardViewProps) {
  const [openCardId, setOpenCardId] = useState<CardId | null>(null)
  const closeDialog = useCallback(() => setOpenCardId(null), [])
  const openCard = openCardId ? board.cards[openCardId] : undefined

  return (
    <>
      <BoardDnd boardId={boardId} board={board}>
        <div className="lists">
          {board.listIds.map((listId) => {
            const list = board.lists[listId]!
            const cards = selectCardsOf(board, listId)
            return (
              <SortableList key={listId} listId={listId} listName={list.name}>
                {(handle) => (
                  <ListView
                    boardId={boardId}
                    list={list}
                    handle={handle}
                    footer={
                      <CreateCardForm boardId={boardId} listId={listId} listName={list.name} />
                    }
                  >
                    <SortableContext
                      id={listId}
                      items={[...list.cardIds]}
                      strategy={verticalListSortingStrategy}
                    >
                      <ul className="cards" aria-label={`Tarjetas de ${list.name}`}>
                        {cards.map((card) => (
                          <SortableCard
                            key={card.id}
                            card={card}
                            onOpen={(c) => setOpenCardId(c.id)}
                          />
                        ))}
                      </ul>
                    </SortableContext>
                  </ListView>
                )}
              </SortableList>
            )
          })}
          <CreateListForm boardId={boardId} />
        </div>
      </BoardDnd>
      {openCardId && <CardDialog boardId={boardId} card={openCard} onClose={closeDialog} />}
    </>
  )
}
