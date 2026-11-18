import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPorts, devToken } from './create-ports'

afterEach(() => vi.unstubAllEnvs())

describe('devToken (solo desarrollo)', () => {
  it('sin VITE_DEV_TOKEN no hay token', () => {
    vi.stubEnv('VITE_DEV_TOKEN', '')
    expect(devToken()).toBeNull()
  })

  it('en dev devuelve VITE_DEV_TOKEN', () => {
    vi.stubEnv('VITE_DEV_TOKEN', 'jwt-de-prueba')
    expect(devToken()).toBe('jwt-de-prueba')
    expect(createPorts().auth.getToken()).toBe('jwt-de-prueba')
  })

  it('fuera de dev (build de produccion) se ignora', () => {
    vi.stubEnv('VITE_DEV_TOKEN', 'jwt-de-prueba')
    vi.stubEnv('DEV', false)
    expect(devToken()).toBeNull()
  })
})
