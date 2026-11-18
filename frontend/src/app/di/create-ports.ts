import type { Ports } from './ports-context'

/** Composition root: aquí (y solo aquí) se instancian los adapters concretos. */
export function createPorts(): Ports {
  return {
    auth: { getToken: () => null },
  }
}
