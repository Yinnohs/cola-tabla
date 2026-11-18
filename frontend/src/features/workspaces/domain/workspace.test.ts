import { describe, expect, it } from 'vitest'
import { workspaceName } from './workspace'

describe('workspaceName', () => {
  it('recorta y acepta nombres validos', () => {
    expect(workspaceName('  Equipo  ')).toEqual({ ok: true, value: 'Equipo' })
  })
  it('rechaza vacio y demasiado largo', () => {
    expect(workspaceName('   ')).toEqual({ ok: false, error: 'EMPTY' })
    expect(workspaceName('x'.repeat(121))).toEqual({ ok: false, error: 'TOO_LONG' })
    expect(workspaceName('x'.repeat(120)).ok).toBe(true)
  })
})
