import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId } from '../../../shared/kernel'
import { normalizeBoard, type NormalizedBoard } from '../domain/normalized-board'
import { boardKeys } from './query-keys'

/** Carga el tablero y lo guarda normalizado (listas/tarjetas por id) en la cache. */
export function useBoard(id: BoardId) {
  const { boards } = usePorts()
  return useQuery<NormalizedBoard>({
    queryKey: boardKeys.detail(id),
    queryFn: async ({ signal }) => normalizeBoard(await boards.get(id, signal)),
  })
}
