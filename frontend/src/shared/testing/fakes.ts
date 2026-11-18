import type { Ports } from '../../app/di'

export function fakeAuthPort(token: string | null = 'test-token'): Ports['auth'] {
  return { getToken: () => token }
}

/** Puertos fake en memoria; cada test puede sobreescribir los que necesite. */
export function fakePorts(overrides: Partial<Ports> = {}): Ports {
  return { auth: fakeAuthPort(), ...overrides }
}
