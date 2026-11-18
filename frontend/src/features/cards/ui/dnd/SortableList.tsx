import type { ReactNode } from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { ListId } from '../../../../shared/kernel'

interface SortableListProps {
  listId: ListId
  listName: string
  /** Recibe el asa de arrastre para que `ListView` la pinte en su cabecera. */
  children: (handle: ReactNode) => ReactNode
}

/** Columna reordenable. Solo el asa (boton) activa el arrastre de la lista; el resto sigue siendo usable. */
export function SortableList({ listId, listName, children }: SortableListProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
    isOver,
  } = useSortable({ id: listId, data: { type: 'list' } })

  const handle = (
    <button
      type="button"
      ref={setActivatorNodeRef}
      {...attributes}
      {...listeners}
      className="drag-handle"
      aria-label={`Mover lista ${listName}`}
    >
      ⠿
    </button>
  )

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={['list-slot', isDragging && 'list-slot--dragging', isOver && 'list-slot--over']
        .filter(Boolean)
        .join(' ')}
    >
      {children(handle)}
    </div>
  )
}
