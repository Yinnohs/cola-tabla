import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router'
import { App } from './app/App'
import { PortsProvider } from './app/di'
import { createPorts } from './app/di/create-ports'
import {
  AccessNoticesContext,
  AuthProvider,
  createAccessNotices,
  createAppQueryClient,
} from './app/providers'
import './styles.css'

const ports = createPorts()
const notices = createAccessNotices()
const queryClient = createAppQueryClient({ auth: ports.auth, notices })

// Restaura la sesion (sessionStorage) ANTES de pintar: sin efectos, el store externo avisa al terminar.
void ports.auth.initialize()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <PortsProvider ports={ports}>
        <AccessNoticesContext.Provider value={notices}>
          <AuthProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </AuthProvider>
        </AccessNoticesContext.Provider>
      </PortsProvider>
    </QueryClientProvider>
  </StrictMode>,
)
