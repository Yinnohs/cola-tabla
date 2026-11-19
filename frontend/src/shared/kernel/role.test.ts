import { describe, expect, it } from 'vitest'
import { canAdmin, canEdit, isRole, toRole } from './role'

describe('role', () => {
  it('VIEWER solo lee; EDITOR edita; OWNER edita y administra', () => {
    expect([canEdit('VIEWER'), canEdit('EDITOR'), canEdit('OWNER')]).toEqual([false, true, true])
    expect([canAdmin('VIEWER'), canAdmin('EDITOR'), canAdmin('OWNER')]).toEqual([
      false,
      false,
      true,
    ])
  })

  it('toRole acepta los roles del backend y rechaza el resto', () => {
    expect(toRole('EDITOR')).toBe('EDITOR')
    expect(() => toRole('BOSS')).toThrow(/desconocido/)
    expect(isRole('owner')).toBe(false)
  })
})
