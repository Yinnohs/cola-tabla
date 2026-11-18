import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId } from '../../../shared/kernel'
import { boardName } from '../domain/board'
import { withBoard, type NormalizedBoard } from '../domain/normalized-board'
import { InvalidBoardNameError } from './use-create-board'
import { boardKeys } from './query-keys'

/**
 * Renombra el tablero con control de concurrencia optimista: usa la `version` cacheada.
 * Ante un conflicto (STALE_VERSION u otro error) se refresca el tablero desde el servidor.
 */
export function useRenameBoard(id: BoardId) {
  const { boards } = usePorts()
  const queryClient = useQueryClient()
  const key = boardKeys.detail(id)
  return useMutation({
    mutationFn: async (name: string) => {
      const valid = boardName(name)
      if (!valid.ok) throw new InvalidBoardNameError(valid.error)
      const cached = queryClient.getQueryData<NormalizedBoard>(key)
      if (!cached) throw new Error('El tablero no esta cargado')
      return boards.rename(id, valid.value, cached.board.version)
    },
    onSuccess: (board) => {
      queryClient.setQueryData<NormalizedBoard>(key, (old) => (old ? withBoard(old, board) : old))
    },
    onError: () => queryClient.invalidateQueries({ queryKey: key }),
  })
}
