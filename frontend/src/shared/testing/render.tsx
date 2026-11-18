import type { ReactElement, ReactNode } from 'react'
import { render, renderHook, type RenderOptions } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PortsProvider, type Ports } from '../../app/di'
import { fakePorts } from './fakes'

export interface TestOptions {
  ports?: Partial<Ports>
  queryClient?: QueryClient
}

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  })
}

function makeWrapper({ ports, queryClient }: TestOptions) {
  const client = queryClient ?? createTestQueryClient()
  const allPorts = fakePorts(ports)
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>
        <PortsProvider ports={allPorts}>{children}</PortsProvider>
      </QueryClientProvider>
    )
  }
}

export function renderWithPorts(
  ui: ReactElement,
  options: TestOptions & Omit<RenderOptions, 'wrapper'> = {},
) {
  const { ports, queryClient, ...rest } = options
  return render(ui, { wrapper: makeWrapper({ ports, queryClient }), ...rest })
}

export function renderHookWithPorts<R>(hook: () => R, options: TestOptions = {}) {
  return renderHook(hook, { wrapper: makeWrapper(options) })
}
