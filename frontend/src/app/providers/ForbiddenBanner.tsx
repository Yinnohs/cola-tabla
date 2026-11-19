import { useForbiddenNotice } from './access-notices'

/** Aviso (no bloqueante) cuando el backend rechaza una accion con 403. */
export function ForbiddenBanner() {
  const { forbidden, dismiss } = useForbiddenNotice()
  if (!forbidden) return null
  return (
    <div role="alert" className="notice notice--forbidden">
      <span>No tienes permiso para realizar esa acción.</span>
      <button type="button" className="btn-ghost" onClick={dismiss}>
        Cerrar aviso
      </button>
    </div>
  )
}
