import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId, ListId } from '../../../shared/kernel'
import { boardKeys, reconcileList, upsertList, type NormalizedBoard } from '../../boards'
import { listName } from '../domain/list'
import { InvalidListNameError } from './errors'
import { toListEntity } from './to-entity'

export interface RenameListInput {
  readonly listId: ListId
  readonly name: string
}

/** Renombra con optimistic update; ante cualquier error se revierte y se refresca el tablero. */
export function useRenameList(boardId: BoardId) {
  const { lists } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  return useMutation({
    scope: { id: key.join('/') },
    mutationFn: async ({ listId, name }: RenameListInput) => {
      const valid = listName(name)
      if (!valid.ok) throw new InvalidListNameError(valid.error)
      const cached = queryClient.getQueryData<NormalizedBoard>(key)?.lists[listId]
      if (!cached) throw new Error('La lista no esta cargada')
      return lists.rename(listId, valid.value, cached.version)
    },
    onMutate: async ({ listId, name }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const snapshot = queryClient.getQueryData<NormalizedBoard>(key)
      const valid = listName(name)
      const current = snapshot?.lists[listId]
      if (snapshot && current && valid.ok) {
        const { cardIds: _cardIds, ...entity } = current
        void _cardIds
        queryClient.setQueryData<NormalizedBoard>(
          key,
          upsertList(snapshot, { ...entity, name: valid.value }),
        )
      }
      return { snapshot }
    },
    onError: (_error, _input, context) => {
      if (context?.snapshot) queryClient.setQueryData(key, context.snapshot)
      void queryClient.invalidateQueries({ queryKey: key })
    },
    onSuccess: (list) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) =>
        old ? reconcileList(old, toListEntity(list)) : old,
      )
    },
  })
}
