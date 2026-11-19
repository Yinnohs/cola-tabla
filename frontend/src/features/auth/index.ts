// API publica de la feature auth (unico punto de import desde fuera)
export type { AuthPort } from './application/ports/auth-port'
export {
  ANONYMOUS_SESSION,
  LOADING_SESSION,
  authenticated,
  safeReturnTo,
  type AuthSession,
  type AuthUser,
} from './domain/auth-session'
export { useSession } from './application/use-session'
export { useAuthActions } from './application/use-auth-actions'
export { ProtectedRoute } from './ui/ProtectedRoute'
export { AuthCallback } from './ui/AuthCallback'
export { SignedOut } from './ui/SignedOut'
export { UserMenu } from './ui/UserMenu'
