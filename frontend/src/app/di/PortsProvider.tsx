import type { ReactNode } from 'react'
import { PortsContext, type Ports } from './ports-context'

export function PortsProvider({ ports, children }: { ports: Ports; children: ReactNode }) {
  return <PortsContext.Provider value={ports}>{children}</PortsContext.Provider>
}
