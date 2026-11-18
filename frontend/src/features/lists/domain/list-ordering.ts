import { planMove, type ListId, type MovePlan, type Positioned } from '../../../shared/kernel'

/** Plan para soltar `listId` en `index` (sobre las demas listas ya ordenadas). */
export function planListMove(
  orderedLists: readonly Positioned<ListId>[],
  listId: ListId,
  index: number,
): MovePlan<ListId> {
  return planMove(orderedLists, listId, index)
}
