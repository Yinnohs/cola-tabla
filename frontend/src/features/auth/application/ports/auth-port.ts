export interface AuthPort {
  /** Token de acceso vigente (o null si no hay sesión). */
  getToken(): string | null
}
