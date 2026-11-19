import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import { StaleVersionError } from '../../../shared/lib'
import type { BoardId, CardId } from '../../../shared/kernel'
import { boardKeys, reconcileCard, type NormalizedBoard } from '../../boards'
import { cardDescription, cardTitle } from '../domain/card'
import { CardConflictError, InvalidCardDescriptionError, InvalidCardTitleError } from './errors'

export interface UpdateCardInput {
  readonly cardId: CardId
  readonly title: string
  readonly description: string
  /** Version con la que el usuario cargo el formulario; por defecto la de la cache. */
  readonly expectedVersion?: number
}

/**
 * Edita titulo/descripcion enviando la `version` cacheada. Si el servidor responde STALE_VERSION se
 * refresca el tablero (la cache queda con la version vigente) y se lanza `CardConflictError`.
 */
export function useUpdateCard(boardId: BoardId) {
  const { cards } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  return useMutation({
    scope: { id: key.join('/') },
    mutationFn: async ({ cardId, title, description, expectedVersion }: UpdateCardInput) => {
      const t = cardTitle(title)
      if (!t.ok) throw new InvalidCardTitleError(t.error)
      const d = cardDescription(description)
      if (!d.ok) throw new InvalidCardDescriptionError(d.error)
      const cached = queryClient.getQueryData<NormalizedBoard>(key)?.cards[cardId]
      if (!cached) throw new Error('La tarjeta no esta cargada')
      try {
        return await cards.update(cardId, {
          title: t.value,
          description: d.value,
          version: expectedVersion ?? cached.version,
        })
      } catch (error) {
        if (error instanceof StaleVersionError) {
          await queryClient.invalidateQueries({ queryKey: key })
          throw new CardConflictError()
        }
        throw error
      }
    },
    onSuccess: (card) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) =>
        old ? reconcileCard(old, card) : old,
      )
    },
  })
}
