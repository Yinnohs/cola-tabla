import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App } from './app/App'
import { PortsProvider } from './app/di'
import { createPorts } from './app/di/create-ports'

const queryClient = new QueryClient()
const ports = createPorts()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <PortsProvider ports={ports}>
        <App />
      </PortsProvider>
    </QueryClientProvider>
  </StrictMode>,
)
