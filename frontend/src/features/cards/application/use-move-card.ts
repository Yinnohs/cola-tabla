import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId, CardId, ListId } from '../../../shared/kernel'
import { boardKeys, selectCardsOf, upsertCard, type NormalizedBoard } from '../../boards'
import { planCardMove, type CardMovePlan } from '../domain/card-ordering'

/**
 * Mueve una tarjeta (misma lista o entre listas) con optimistic update:
 * 1. `moveCard` calcula vecinos + posicion local sobre la cache actual (card-ordering).
 * 2. onMutate aplica la posicion/lista nueva al instante (la UI reordena sin esperar).
 * 3. Se envian los ids de los vecinos; la respuesta (position/version del servidor) reconcilia la cache.
 * 4. Si falla, se restaura la cache previa y se refresca el tablero.
 */
export function useMoveCard(boardId: BoardId) {
  const { cards } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  const mutation = useMutation({
    scope: { id: key.join('/') },
    mutationFn: ({ cardId, toListId, prevCardId, nextCardId }: CardMovePlan) =>
      cards.move(cardId, { listId: toListId, prevCardId, nextCardId }),
    onMutate: async ({ cardId, toListId, position }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const snapshot = queryClient.getQueryData<NormalizedBoard>(key)
      const current = snapshot?.cards[cardId]
      if (snapshot && current) {
        queryClient.setQueryData<NormalizedBoard>(
          key,
          upsertCard(snapshot, { ...current, listId: toListId, position }),
        )
      }
      return { snapshot }
    },
    onError: (_error, _plan, context) => {
      if (context?.snapshot) queryClient.setQueryData(key, context.snapshot)
      void queryClient.invalidateQueries({ queryKey: key })
    },
    onSuccess: (card) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) => (old ? upsertCard(old, card) : old))
    },
  })

  const { mutate } = mutation
  /**
   * `index` es la posicion de destino dentro de `toListId` contando las demas tarjetas (sin la movida).
   * Devuelve el plan aplicado, o null si la tarjeta no existe o ya estaba ahi.
   */
  function moveCard(cardId: CardId, toListId: ListId, index: number): CardMovePlan | null {
    const nb = queryClient.getQueryData<NormalizedBoard>(key)
    if (!nb?.cards[cardId] || !nb.lists[toListId]) return null
    const plan = planCardMove(selectCardsOf(nb, toListId), cardId, toListId, index)
    if (plan.unchanged && nb.cards[cardId].listId === toListId) return null
    mutate(plan)
    return plan
  }

  return { ...mutation, moveCard }
}
