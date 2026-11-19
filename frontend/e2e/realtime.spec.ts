import { expect, test, type Page } from '@playwright/test'
import { loginAndOpen } from './support/login'
import { EDITOR, OWNER, seedBoard, type SeededBoard } from './support/seed'

/**
 * DoD de la Fase 3: dos navegadores (owner y editor) sobre el mismo tablero; lo que se hace en A se ve en B
 * sin recargar. Requiere el stack levantado a mano (ver e2e/README.md).
 */
test.describe('tiempo real entre dos navegadores', () => {
  let seeded: SeededBoard
  let ownerPage: Page
  let editorPage: Page

  test.beforeEach(async ({ browser }) => {
    seeded = await seedBoard()
    const path = `/boards/${seeded.boardId}`
    // Dos contextos aislados = dos usuarios con sesiones distintas.
    ownerPage = await (await browser.newContext()).newPage()
    editorPage = await (await browser.newContext()).newPage()
    await Promise.all([
      loginAndOpen(ownerPage, OWNER, path),
      loginAndOpen(editorPage, EDITOR, path),
    ])
    for (const page of [ownerPage, editorPage]) {
      await expect(page.getByRole('region', { name: `Lista ${seeded.lists.todo}` })).toBeVisible()
    }
  })

  test.afterEach(async () => {
    await ownerPage.context().close()
    await editorPage.context().close()
  })

  const list = (page: Page, name: string) => page.getByRole('region', { name: `Lista ${name}` })

  test('mover una tarjeta en A se ve en B', async () => {
    const [title] = seeded.cards
    const card = ownerPage.locator('.card-handle', { hasText: title })
    await expect(list(editorPage, seeded.lists.todo)).toContainText(title!)

    // Teclado de dnd-kit: Espacio recoge, flecha derecha cambia de lista, Espacio suelta.
    await card.focus()
    await ownerPage.keyboard.press('Space')
    await ownerPage.keyboard.press('ArrowRight')
    await ownerPage.keyboard.press('Space')

    await expect(list(ownerPage, seeded.lists.doing)).toContainText(title!)
    // B recibe el evento por WebSocket (sin recargar la pagina)
    await expect(list(editorPage, seeded.lists.doing)).toContainText(title!)
    await expect(list(editorPage, seeded.lists.todo)).not.toContainText(title!)
  })

  test('crear una tarjeta en B se ve en A', async () => {
    const title = `Nueva ${Date.now().toString(36)}`
    await editorPage
      .getByRole('button', { name: /Añadir tarjeta/ })
      .first()
      .click()
    await editorPage.getByLabel('Título de la tarjeta').fill(title)
    await editorPage.getByRole('button', { name: 'Añadir', exact: true }).click()

    await expect(list(editorPage, seeded.lists.todo)).toContainText(title)
    await expect(list(ownerPage, seeded.lists.todo)).toContainText(title)
  })
})
