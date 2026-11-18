import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId, ListId } from '../../../shared/kernel'
import { boardKeys, upsertCard, type NormalizedBoard } from '../../boards'
import { cardDescription, cardTitle } from '../domain/card'
import { InvalidCardDescriptionError, InvalidCardTitleError } from './errors'

export interface CreateCardInput {
  readonly listId: ListId
  readonly title: string
  readonly description?: string
}

export function useCreateCard(boardId: BoardId) {
  const { cards } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(boardId)
  return useMutation({
    scope: { id: key.join('/') },
    mutationFn: async ({ listId, title, description }: CreateCardInput) => {
      const t = cardTitle(title)
      if (!t.ok) throw new InvalidCardTitleError(t.error)
      const d = cardDescription(description ?? '')
      if (!d.ok) throw new InvalidCardDescriptionError(d.error)
      return cards.create(listId, t.value, d.value === '' ? undefined : d.value)
    },
    onSuccess: (card) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) => (old ? upsertCard(old, card) : old))
    },
  })
}
