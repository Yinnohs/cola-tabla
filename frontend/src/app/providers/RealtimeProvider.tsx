import type { ReactNode } from 'react'
import type { BoardId } from '../../shared/kernel'
import { useBoardSync } from '../../features/realtime'
import { RealtimeStatusContext } from './realtime-status'

/**
 * Mantiene el tablero `boardId` sincronizado por WebSocket mientras este montado (sin `useEffect`: ver
 * `useBoardSync`, ADR-0010). El WS solo notifica: la fuente de verdad sigue siendo `GET /api/boards/{id}`.
 * Muestra solo el aviso minimo "sin acceso" (el indicador de conexion elaborado es de la Fase 4).
 */
export function RealtimeProvider({ boardId, children }: { boardId: BoardId; children: ReactNode }) {
  const { connection } = useBoardSync(boardId)
  return (
    <RealtimeStatusContext.Provider value={connection}>
      {connection === 'access-denied' && (
        <p role="alert" className="notice">
          Sin acceso a las actualizaciones en tiempo real de este tablero.
        </p>
      )}
      {children}
    </RealtimeStatusContext.Provider>
  )
}
