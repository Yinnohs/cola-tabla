import { closestCenter, closestCorners, type CollisionDetection } from '@dnd-kit/core'
import { kindOf } from './drop'

/**
 * Deteccion de colisiones del tablero.
 * - Una lista solo se suelta sobre listas.
 * - Una tarjeta: primero se decide la columna por la posicion horizontal de su centro (las columnas
 *   son altas, `closestCorners` las penaliza) y dentro de ella se elige la tarjeta mas cercana; si la
 *   lista esta vacia, el destino es la propia lista.
 */
export const boardCollisionDetection: CollisionDetection = (args) => {
  const lists = args.droppableContainers.filter((c) => c.data.current?.type === 'list')
  if (kindOf(args.active) === 'list') {
    return closestCorners({ ...args, droppableContainers: lists })
  }

  const centerX = args.collisionRect.left + args.collisionRect.width / 2
  const column =
    lists.find((list) => {
      const rect = args.droppableRects.get(list.id)
      return rect !== undefined && centerX >= rect.left && centerX <= rect.right
    }) ?? closestCenter({ ...args, droppableContainers: lists })[0]?.data?.droppableContainer
  if (!column) return []

  const cards = args.droppableContainers.filter(
    (c) => c.data.current?.type === 'card' && c.data.current.listId === column.id,
  )
  if (cards.length === 0) {
    return [{ id: column.id, data: { droppableContainer: column, value: 0 } }]
  }
  return closestCenter({ ...args, droppableContainers: cards })
}
