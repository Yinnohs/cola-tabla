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
import { ReadOnlyCard } from './ReadOnlyCard'

interface BoardViewProps {
  boardId: BoardId
  board: NormalizedBoard
  /**
   * false (rol VIEWER): sin formularios de alta, renombrado, borrado ni arrastre; NO se monta DndContext.
   * El backend sigue siendo quien decide (403); esto solo evita ofrecer acciones que fallarian.
   */
  canEdit?: boolean
}

/** Tablero completo: listas reordenables con tarjetas arrastrables, formularios de alta y dialogo de edicion. */
export function BoardView({ boardId, board, canEdit = true }: BoardViewProps) {
  const [openCardId, setOpenCardId] = useState<CardId | null>(null)
  const closeDialog = useCallback(() => setOpenCardId(null), [])
  const openCard = openCardId ? board.cards[openCardId] : undefined
  // La tarjeta desaparecio (borrada por otra persona): se descarta el id durante el render, sin efecto.
  if (openCardId && !openCard) setOpenCardId(null)

  if (!canEdit) {
    return (
      <>
        <div className="lists">
          {board.listIds.map((listId) => {
            const list = board.lists[listId]!
            return (
              <div key={listId} className="list-slot">
                <ListView boardId={boardId} list={list} canEdit={false}>
                  <ul className="cards" aria-label={`Tarjetas de ${list.name}`}>
                    {selectCardsOf(board, listId).map((card) => (
                      <ReadOnlyCard key={card.id} card={card} onOpen={(c) => setOpenCardId(c.id)} />
                    ))}
                  </ul>
                </ListView>
              </div>
            )
          })}
        </div>
        {openCard && (
          <CardDialog boardId={boardId} card={openCard} onClose={closeDialog} readOnly />
        )}
      </>
    )
  }

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
      {openCard && <CardDialog boardId={boardId} card={openCard} onClose={closeDialog} />}
    </>
  )
}
