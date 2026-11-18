import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId, CardId } from '../../../shared/kernel'
import { boardKeys, removeCard, type NormalizedBoard } from '../../boards'

/** Borra la tarjeta con optimistic update (desaparece al instante); si falla, se restaura. */
export function useDeleteCard(boardId: BoardId) {
  const { cards } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  return useMutation({
    scope: { id: key.join('/') },
    mutationFn: (id: CardId) => cards.delete(id),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: key })
      const snapshot = queryClient.getQueryData<NormalizedBoard>(key)
      if (snapshot) queryClient.setQueryData<NormalizedBoard>(key, removeCard(snapshot, id))
      return { snapshot }
    },
    onError: (_error, _id, context) => {
      if (context?.snapshot) queryClient.setQueryData(key, context.snapshot)
      void queryClient.invalidateQueries({ queryKey: key })
    },
  })
}
