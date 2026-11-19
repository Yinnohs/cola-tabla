import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId, FractionalIndex, ListId } from '../../../shared/kernel'
import { boardKeys, reconcileList, upsertList, type NormalizedBoard } from '../../boards'
import { planListMove } from '../domain/list-ordering'
import { toListEntity } from './to-entity'

/** Comando ya resuelto: vecinos para el servidor + posicion local optimista. */
export interface MoveListCommand {
  readonly listId: ListId
  readonly prevListId: ListId | null
  readonly nextListId: ListId | null
  readonly position: FractionalIndex
}

/**
 * Reordena listas. `moveList(listId, index)` calcula (con `FractionalIndex.between`) los vecinos y la
 * posicion optimista sobre la cache actual, la aplica al instante y llama al servidor; si falla se revierte.
 * La respuesta del servidor (position/version) reconcilia la cache.
 */
export function useMoveList(boardId: BoardId) {
  const { lists } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  const mutation = useMutation({
    scope: { id: key.join('/') },
    mutationFn: ({ listId, prevListId, nextListId }: MoveListCommand) =>
      lists.move(listId, { prevListId, nextListId }),
    onMutate: async ({ listId, position }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const snapshot = queryClient.getQueryData<NormalizedBoard>(key)
      const current = snapshot?.lists[listId]
      if (snapshot && current) {
        const { cardIds: _cardIds, ...entity } = current
        void _cardIds
        queryClient.setQueryData<NormalizedBoard>(
          key,
          upsertList(snapshot, { ...entity, position }),
        )
      }
      return { snapshot }
    },
    onError: (_error, _cmd, context) => {
      if (context?.snapshot) queryClient.setQueryData(key, context.snapshot)
      void queryClient.invalidateQueries({ queryKey: key })
    },
    onSuccess: (list) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) =>
        old ? reconcileList(old, toListEntity(list)) : old,
      )
    },
  })

  const { mutate } = mutation
  /** `index` sobre las demas listas (sin la movida). Devuelve false si no hay nada que hacer. */
  function moveList(listId: ListId, index: number): boolean {
    const nb = queryClient.getQueryData<NormalizedBoard>(key)
    if (!nb?.lists[listId]) return false
    const ordered = nb.listIds.map((id) => nb.lists[id]!)
    const plan = planListMove(ordered, listId, index)
    if (plan.unchanged) return false
    mutate({
      listId,
      prevListId: plan.prevId,
      nextListId: plan.nextId,
      position: plan.position,
    })
    return true
  }

  return { ...mutation, moveList }
}
