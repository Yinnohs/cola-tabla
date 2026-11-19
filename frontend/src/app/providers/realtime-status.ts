import { createContext, useContext } from 'react'
import type { ConnectionStatus } from '../../features/realtime'

/** Estado de la conexion en tiempo real del tablero abierto (lo usara el indicador de Fase 4). */
export const RealtimeStatusContext = createContext<ConnectionStatus | null>(null)

export function useRealtimeStatus(): ConnectionStatus | null {
  return useContext(RealtimeStatusContext)
}
