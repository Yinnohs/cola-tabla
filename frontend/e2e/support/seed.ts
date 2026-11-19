/**
 * Seed por API REST con el cliente `kanban-dev-cli` de Keycloak (password grant, solo desarrollo).
 * Usa el `fetch` de Node (este codigo NO forma parte de src/ ni del bundle).
 */
const KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180/realms/kanban'
const API = process.env.E2E_API_URL ?? 'http://localhost:8080'

export interface DevUser {
  username: string
  password: string
}
export const OWNER: DevUser = { username: 'owner', password: 'owner' }
export const EDITOR: DevUser = { username: 'editor', password: 'editor' }

export async function accessToken(user: DevUser): Promise<string> {
  const res = await fetch(`${KEYCLOAK}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'kanban-dev-cli',
      grant_type: 'password',
      username: user.username,
      password: user.password,
    }),
  })
  if (!res.ok)
    throw new Error(`Login de ${user.username} fallido: ${res.status} ${await res.text()}`)
  return ((await res.json()) as { access_token: string }).access_token
}

/** `sub` del JWT (es el userId que espera POST /members). */
export function subOf(token: string): string {
  const payload = token.split('.')[1]
  if (!payload) throw new Error('JWT mal formado')
  return (JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as { sub: string }).sub
}

async function call<T>(token: string, method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${await res.text()}`)
  return (res.status === 204 ? undefined : await res.json()) as T
}

export interface SeededBoard {
  boardId: string
  workspaceId: string
  /** Nombres de listas y tarjetas sembradas (para localizarlas en la UI). */
  lists: { todo: string; doing: string }
  cards: string[]
}

/**
 * El owner crea workspace + tablero (listas "Por hacer" y "En curso", 2 tarjetas en la primera) y anade
 * al editor como miembro EDITOR (el rol real sale de la membresia, no de Keycloak).
 */
export async function seedBoard(suffix: string = Date.now().toString(36)): Promise<SeededBoard> {
  const ownerToken = await accessToken(OWNER)
  const editorSub = subOf(await accessToken(EDITOR))

  const ws = await call<{ id: string }>(ownerToken, 'POST', '/api/workspaces', {
    name: `E2E ${suffix}`,
  })
  await call(ownerToken, 'POST', `/api/workspaces/${ws.id}/members`, {
    userId: editorSub,
    role: 'EDITOR',
  })
  const board = await call<{ id: string }>(ownerToken, 'POST', `/api/workspaces/${ws.id}/boards`, {
    name: `Tablero ${suffix}`,
  })
  const todo = await call<{ id: string }>(ownerToken, 'POST', `/api/boards/${board.id}/lists`, {
    name: 'Por hacer',
  })
  await call(ownerToken, 'POST', `/api/boards/${board.id}/lists`, { name: 'En curso' })
  const cards = ['Preparar demo', 'Escribir informe']
  for (const title of cards) {
    await call(ownerToken, 'POST', `/api/lists/${todo.id}/cards`, { title })
  }
  return {
    boardId: board.id,
    workspaceId: ws.id,
    lists: { todo: 'Por hacer', doing: 'En curso' },
    cards,
  }
}
