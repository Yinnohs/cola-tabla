import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId } from '../../../shared/kernel'
import { boardKeys, upsertList, type NormalizedBoard } from '../../boards'
import { listName } from '../domain/list'
import { InvalidListNameError } from './errors'
import { toListEntity } from './to-entity'

export function useCreateList(boardId: BoardId) {
  const { lists } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  return useMutation({
    scope: { id: key.join('/') },
    mutationFn: async (name: string) => {
      const valid = listName(name)
      if (!valid.ok) throw new InvalidListNameError(valid.error)
      return lists.create(boardId, valid.value)
    },
    onSuccess: (list) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) =>
        old ? upsertList(old, toListEntity(list)) : old,
      )
    },
  })
}
