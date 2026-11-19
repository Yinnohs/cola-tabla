import { usePorts } from '../../../app/di'

/** Acciones de sesion para botones: iniciar y cerrar sesion. */
export function useAuthActions() {
  const { auth } = usePorts()
  return {
    login: (returnTo = '/') => auth.login(returnTo),
    logout: () => auth.logout(),
  }
}
