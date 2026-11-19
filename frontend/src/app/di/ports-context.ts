import { createContext, useContext } from 'react'
import type { AuthPort } from '../../features/auth'
import type { BoardRepository } from '../../features/boards'
import type { CardRepository } from '../../features/cards'
import type { ListRepository } from '../../features/lists'
import type { BoardEventsPort } from '../../features/realtime'
import type { MemberRepository, WorkspaceRepository } from '../../features/workspaces'

/** Todos los puertos que los casos de uso pueden necesitar. Se amplía por feature. */
export interface Ports {
  auth: AuthPort
  workspaces: WorkspaceRepository
  members: MemberRepository
  boards: BoardRepository
  lists: ListRepository
  cards: CardRepository
  /** Eventos de tablero en tiempo real (STOMP) y su log REST. */
  events: BoardEventsPort
  /** Identificador de esta pestana (`X-Client-Id`): permite ignorar el eco de las propias escrituras. */
  clientId: string
}

export const PortsContext = createContext<Ports | null>(null)

export function usePorts(): Ports {
  const ports = useContext(PortsContext)
  if (!ports) throw new Error('usePorts must be used inside <PortsProvider>')
  return ports
}
