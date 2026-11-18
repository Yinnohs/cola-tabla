import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import { workspaceName, type WorkspaceNameError } from '../domain/workspace'
import { workspaceKeys } from './query-keys'

/** Error de validacion de dominio (antes de llamar al backend). */
export class InvalidWorkspaceNameError extends Error {
  readonly reason: WorkspaceNameError
  constructor(reason: WorkspaceNameError) {
    super(reason === 'EMPTY' ? 'El nombre es obligatorio' : 'El nombre es demasiado largo')
    this.name = 'InvalidWorkspaceNameError'
    this.reason = reason
  }
}

export function useCreateWorkspace() {
  const { workspaces } = usePorts()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (name: string) => {
      const valid = workspaceName(name)
      if (!valid.ok) throw new InvalidWorkspaceNameError(valid.error)
      return workspaces.create(valid.value)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: workspaceKeys.list() }),
  })
}
