import { afterAll, afterEach, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'

export const TEST_API_URL = 'http://api.test'

/** Registra un servidor MSW estricto (peticion sin handler = error) con ciclo de vida de vitest. */
export function setupMswServer() {
  const server = setupServer()
  beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
  afterEach(() => server.resetHandlers())
  afterAll(() => server.close())
  return server
}
