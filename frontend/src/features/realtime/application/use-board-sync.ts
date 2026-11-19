import { useMemo, useSyncExternalStore } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { BoardId } from '../../../shared/kernel'
import { createBoardSyncSession, type BoardSyncSnapshot } from './board-sync-session'

/**
 * Mantiene sincronizado el tablero `boardId` con el tiempo real mientras el componente esta montado.
 * Sin `useEffect`: la sesion es un store externo y `useSyncExternalStore` abre la suscripcion al montar y la
 * cierra al desmontar. Devuelve el estado de la conexion (el indicador elaborado llega en Fase 4).
 * Requiere que el tablero este (o vaya a estar) en la cache `boardKeys.detail(boardId)` (p. ej. via `useBoard`).
 */
export function useBoardSync(boardId: BoardId): BoardSyncSnapshot {
  const { events, clientId } = usePorts()
  const queryClient = useQueryClient()
  const session = useMemo(
    () => createBoardSyncSession({ boardId, events, queryClient, clientId }),
    [boardId, events, queryClient, clientId],
  )
  return useSyncExternalStore(session.subscribe, session.getSnapshot)
}
