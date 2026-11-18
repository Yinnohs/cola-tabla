import { createContext, useContext } from 'react'
import type { AuthPort } from '../../features/auth'

/** Todos los puertos que los casos de uso pueden necesitar. Se amplía por feature. */
export interface Ports {
  auth: AuthPort
}

export const PortsContext = createContext<Ports | null>(null)

export function usePorts(): Ports {
  const ports = useContext(PortsContext)
  if (!ports) throw new Error('usePorts must be used inside <PortsProvider>')
  return ports
}
