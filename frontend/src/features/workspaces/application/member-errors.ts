import { AppError } from '../../../shared/lib'
import type { MemberUserIdError } from '../domain/member'

/** Error de validacion de dominio al invitar (antes de llamar al backend). */
export class InvalidMemberUserIdError extends Error {
  readonly reason: MemberUserIdError
  constructor(reason: MemberUserIdError) {
    super(
      reason === 'EMPTY'
        ? 'El identificador de usuario es obligatorio'
        : 'El identificador es demasiado largo',
    )
    this.name = 'InvalidMemberUserIdError'
    this.reason = reason
  }
}

/** Mensaje en espanol para los errores de gestion de miembros (codigos del backend). */
export function memberErrorMessage(error: Error): string {
  if (error instanceof AppError) {
    switch (error.code) {
      case 'LAST_OWNER':
        return 'El workspace debe conservar al menos un propietario.'
      case 'ALREADY_MEMBER':
        return 'Esa persona ya es miembro del workspace.'
      case 'NOT_FOUND':
        return 'Ese miembro ya no existe.'
    }
    if (error.status === 403) return 'No tienes permiso para gestionar miembros.'
  }
  return error.message
}
