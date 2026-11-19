import type { Page } from '@playwright/test'
import type { DevUser } from './seed'

/**
 * Login real por el formulario de Keycloak. oidc-client-ts guarda la sesion en sessionStorage (por
 * pestana), asi que NO se usa storageState: cada contexto/pagina hace su propio login y vuelve a `path`.
 */
export async function loginAndOpen(page: Page, user: DevUser, path: string): Promise<void> {
  await page.goto(path)
  await page.waitForURL(/\/realms\/kanban\//)
  await page.locator('#username').fill(user.username)
  await page.locator('#password').fill(user.password)
  await page.locator('#kc-login').click()
  await page.waitForURL((url) => url.pathname === path)
}
