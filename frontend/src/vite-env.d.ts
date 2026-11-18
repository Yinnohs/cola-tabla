/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origen de la API; vacio = mismo origen. */
  readonly VITE_API_BASE_URL?: string
  /** Solo dev: JWT de Keycloak (kanban-dev-cli) para probar a mano sin login. Se ignora en el build. */
  readonly VITE_DEV_TOKEN?: string
}
