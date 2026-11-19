import { describe, expect, it } from 'vitest'
import { authenticated, safeReturnTo } from './auth-session'

describe('safeReturnTo', () => {
  it('acepta rutas internas con query y hash', () => {
    expect(safeReturnTo('/boards/1?x=1#a')).toBe('/boards/1?x=1#a')
  })

  it.each([
    'https://evil.test/',
    '//evil.test',
    '/' + String.fromCharCode(92) + 'evil',
    '',
    undefined,
    42,
    '/auth/callback',
  ])('rechaza %j y vuelve a la raiz', (raw) => {
    expect(safeReturnTo(raw)).toBe('/')
  })
})

describe('authenticated', () => {
  it('lleva la persona', () => {
    expect(authenticated({ id: 'u1', name: 'Ana', email: null }).status).toBe('authenticated')
  })
})
