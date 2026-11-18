import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { WorkspaceId } from '../../../shared/kernel'
import { boardName, type BoardNameError } from '../domain/board'
import { boardKeys } from './query-keys'

export class InvalidBoardNameError extends Error {
  readonly reason: BoardNameError
  constructor(reason: BoardNameError) {
    super(reason === 'EMPTY' ? 'El nombre es obligatorio' : 'El nombre es demasiado largo')
    this.name = 'InvalidBoardNameError'
    this.reason = reason
  }
}

export function useCreateBoard(workspaceId: WorkspaceId) {
  const { boards } = usePorts()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (name: string) => {
      const valid = boardName(name)
      if (!valid.ok) throw new InvalidBoardNameError(valid.error)
      return boards.create(workspaceId, valid.value)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: boardKeys.list(workspaceId) }),
  })
}
