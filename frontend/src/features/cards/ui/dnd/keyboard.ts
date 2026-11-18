import type { KeyboardCoordinateGetter } from '@dnd-kit/core'
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import { kindOf } from './drop'

/**
 * `sortableKeyboardCoordinates` considera TODOS los droppables. Al mover una lista con el teclado
 * las tarjetas (que estan dentro de las listas) la despistarian: se filtran para que solo cuenten listas.
 */
export const boardKeyboardCoordinates: KeyboardCoordinateGetter = (event, args) => {
  const { context } = args
  if (kindOf(context.active) !== 'list') return sortableKeyboardCoordinates(event, args)
  const all = context.droppableContainers
  const listsOnly = {
    getEnabled: () => all.getEnabled().filter((c) => c.data.current?.type === 'list'),
    get: (id: Parameters<typeof all.get>[0]) => all.get(id),
  } as unknown as typeof all
  return sortableKeyboardCoordinates(event, {
    ...args,
    context: { ...context, droppableContainers: listsOnly },
  })
}
