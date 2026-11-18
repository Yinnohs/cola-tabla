import type { KeyboardEvent } from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { BoardCardView } from '../../../boards'
import { CardView } from '../CardView'

interface SortableCardProps {
  card: BoardCardView
  onOpen: (card: BoardCardView) => void
}

/**
 * Tarjeta arrastrable: raton (PointerSensor) y teclado (Espacio recoge/suelta, flechas mueven).
 * Click o Enter abren la tarjeta; Enter no inicia el arrastre (ver DND_SENSOR_KEY_CODES).
 */
export function SortableCard({ card, onOpen }: SortableCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging, isOver } =
    useSortable({ id: card.id, data: { type: 'card', listId: card.listId } })

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    listeners?.onKeyDown?.(event)
    if (event.key === 'Enter' && !isDragging) {
      event.preventDefault()
      onOpen(card)
    }
  }

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={['card', isDragging && 'card--dragging', isOver && !isDragging && 'card--over']
        .filter(Boolean)
        .join(' ')}
      data-card-id={card.id}
    >
      <div
        {...attributes}
        {...listeners}
        className="card-handle"
        onKeyDown={onKeyDown}
        onClick={() => onOpen(card)}
      >
        <CardView title={card.title} description={card.description} />
      </div>
    </li>
  )
}
