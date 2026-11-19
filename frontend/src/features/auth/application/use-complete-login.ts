import { useQuery } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'

/** Procesa `/auth/callback` una sola vez (sin useEffect) y devuelve la ruta a la que volver. */
export function useCompleteLogin() {
  const { auth } = usePorts()
  return useQuery({
    queryKey: ['auth', 'callback'],
    queryFn: () => auth.completeLogin(),
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
}
