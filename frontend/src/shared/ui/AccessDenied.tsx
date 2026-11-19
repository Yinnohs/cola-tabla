import type { ReactNode } from 'react'

interface AccessDeniedProps {
  /** Enlace o accion para salir de la pantalla (p. ej. volver a workspaces). */
  children?: ReactNode
}

/** Pantalla de "sin permiso" (403): la sesion es valida pero el rol no alcanza para el recurso. */
export function AccessDenied({ children }: AccessDeniedProps) {
  return (
    <section role="alert" className="access-denied">
      <h1>Sin permiso</h1>
      <p>No tienes permiso para acceder a este recurso o realizar esta acción.</p>
      {children}
    </section>
  )
}
