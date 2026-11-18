import { useMemo, useState, type ReactNode } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type Active,
  type Announcements,
  type DragEndEvent,
} from '@dnd-kit/core'
import { SortableContext, horizontalListSortingStrategy } from '@dnd-kit/sortable'
import type { BoardId, CardId, ListId } from '../../../../shared/kernel'
import type { NormalizedBoard } from '../../../boards'
import { useMoveList } from '../../../lists'
import { useMoveCard } from '../../application/use-move-card'
import { CardView } from '../CardView'
import {
  DND_SENSOR_KEY_CODES,
  SCREEN_READER_INSTRUCTIONS,
  buildAnnouncements,
} from './announcements'
import { boardCollisionDetection } from './collision'
import { boardKeyboardCoordinates } from './keyboard'
import { kindOf, resolveCardDragTarget, resolveListDragIndex } from './drop'

interface BoardDndProps {
  boardId: BoardId
  board: NormalizedBoard
  children: ReactNode
}

/**
 * Contexto de drag & drop del tablero (unico sitio que importa dnd-kit junto a SortableCard/SortableList).
 * Sensores: puntero y teclado (Espacio recoge, flechas mueven, Espacio suelta, Escape cancela), con
 * anuncios para lector de pantalla y una region `aria-live` visible que repite el ultimo mensaje.
 */
export function BoardDnd({ boardId, board, children }: BoardDndProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: boardKeyboardCoordinates,
      keyboardCodes: DND_SENSOR_KEY_CODES,
    }),
  )
  const { moveCard } = useMoveCard(boardId)
  const { moveList } = useMoveList(boardId)
  const [message, setMessage] = useState('')
  const [dragging, setDragging] = useState<Active | null>(null)

  const announcements = useMemo<Announcements>(() => {
    const base = buildAnnouncements(board)
    const mirror =
      <A extends unknown[]>(fn: (...args: A) => string | undefined) =>
      (...args: A) => {
        const text = fn(...args)
        if (text) setMessage(text)
        return text
      }
    return {
      onDragStart: mirror(base.onDragStart!),
      onDragOver: mirror(base.onDragOver!),
      onDragEnd: mirror(base.onDragEnd!),
      onDragCancel: mirror(base.onDragCancel!),
    }
  }, [board])

  function onDragEnd({ active, over }: DragEndEvent) {
    setDragging(null)
    if (kindOf(active) === 'list') {
      const index = resolveListDragIndex(board, active, over)
      if (index !== null) moveList(active.id as ListId, index)
      return
    }
    const drop = resolveCardDragTarget(board, active, over)
    if (drop) moveCard(active.id as CardId, drop.toListId, drop.index)
  }

  const draggedCard =
    dragging && kindOf(dragging) === 'card' ? board.cards[dragging.id as CardId] : undefined

  return (
    <>
      <DndContext
        sensors={sensors}
        collisionDetection={boardCollisionDetection}
        accessibility={{ announcements, screenReaderInstructions: SCREEN_READER_INSTRUCTIONS }}
        onDragStart={({ active }) => setDragging(active)}
        onDragEnd={onDragEnd}
        onDragCancel={() => setDragging(null)}
      >
        <SortableContext items={[...board.listIds]} strategy={horizontalListSortingStrategy}>
          {children}
        </SortableContext>
        <DragOverlay>
          {draggedCard ? (
            <div className="card card--overlay">
              <CardView title={draggedCard.title} description={draggedCard.description} />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>
      <p role="status" aria-live="polite" aria-label="Estado del arrastre" className="dnd-status">
        {message}
      </p>
    </>
  )
}
