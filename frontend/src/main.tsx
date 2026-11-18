import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router'
import { App } from './app/App'
import { PortsProvider } from './app/di'
import { createPorts } from './app/di/create-ports'
import { AppError } from './shared/lib'
import './styles.css'

// Un 4xx (p. ej. 404 de un workspace inexistente) no se arregla reintentando.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (count, error) => !(error instanceof AppError && error.status < 500) && count < 3,
    },
  },
})
const ports = createPorts()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <PortsProvider ports={ports}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </PortsProvider>
    </QueryClientProvider>
  </StrictMode>,
)
