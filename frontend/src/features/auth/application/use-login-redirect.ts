import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'

/**
 * Lanza la redireccion al login al montarse (sin useEffect: la dispara el `queryFn`, una sola vez por ruta).
 * Si la redireccion falla, `isError` permite ofrecer un boton manual.
 */
export function useLoginRedirect(returnTo: string) {
  const { auth } = usePorts()
  return useQuery({
    queryKey: ['auth', 'login-redirect', returnTo],
    queryFn: async () => {
      await auth.login(returnTo)
      return null
    },
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
}
