import { defineConfig, devices } from '@playwright/test'

/**
 * E2E de tiempo real (F3.8). NO arranca el stack: levanta a mano infra + backend + `npm run dev`
 * (ver e2e/README.md). No forma parte de `npm test` (vitest solo busca en src/ y tests/).
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
