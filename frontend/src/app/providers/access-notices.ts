import { createContext, useContext, useSyncExternalStore } from 'react'

/** Aviso global de "sin permiso" (403 en una accion o consulta) como store externo (sin efectos). */
export interface AccessNotices {
  readonly getSnapshot: () => boolean
  readonly subscribe: (listener: () => void) => () => void
  reportForbidden(): void
  dismiss(): void
}

export function createAccessNotices(): AccessNotices {
  let forbidden = false
  const listeners = new Set<() => void>()
  const set = (value: boolean) => {
    if (forbidden === value) return
    forbidden = value
    listeners.forEach((l) => l())
  }
  return {
    getSnapshot: () => forbidden,
    subscribe: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    reportForbidden: () => set(true),
    dismiss: () => set(false),
  }
}

export const AccessNoticesContext = createContext<AccessNotices | null>(null)

const NEVER = { getSnapshot: () => false, subscribe: () => () => {} }

export function useForbiddenNotice(): { forbidden: boolean; dismiss: () => void } {
  const notices = useContext(AccessNoticesContext)
  const source = notices ?? NEVER
  const forbidden = useSyncExternalStore(source.subscribe, source.getSnapshot)
  return { forbidden, dismiss: () => notices?.dismiss() }
}
