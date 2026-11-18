import { createContext, useContext } from 'react'
import type { AuthPort } from '../../features/auth'
import type { BoardRepository } from '../../features/boards'
import type { CardRepository } from '../../features/cards'
import type { ListRepository } from '../../features/lists'
import type { WorkspaceRepository } from '../../features/workspaces'

/** Todos los puertos que los casos de uso pueden necesitar. Se amplía por feature. */
export interface Ports {
  auth: AuthPort
  workspaces: WorkspaceRepository
  boards: BoardRepository
  lists: ListRepository
  cards: CardRepository
}

export const PortsContext = createContext<Ports | null>(null)

export function usePorts(): Ports {
  const ports = useContext(PortsContext)
  if (!ports) throw new Error('usePorts must be used inside <PortsProvider>')
  return ports
}
