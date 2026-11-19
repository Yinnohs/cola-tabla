import { describe, expect, it } from 'vitest'
import { workspaceId } from '../../../shared/kernel'
import { isLastOwner, memberUserId, type Member } from './member'

const ws = workspaceId('w1')
const member = (userId: string, role: Member['role']): Member => ({
  id: userId,
  workspaceId: ws,
  userId,
  role,
  version: 0,
})

describe('memberUserId', () => {
  it('recorta espacios y rechaza vacio o demasiado largo', () => {
    expect(memberUserId('  abc  ')).toEqual({ ok: true, value: 'abc' })
    expect(memberUserId('   ')).toEqual({ ok: false, error: 'EMPTY' })
    expect(memberUserId('x'.repeat(256))).toEqual({ ok: false, error: 'TOO_LONG' })
  })
})

describe('isLastOwner', () => {
  it('el unico OWNER es el ultimo', () => {
    const a = member('a', 'OWNER')
    expect(isLastOwner([a, member('b', 'EDITOR')], a)).toBe(true)
  })

  it('con dos OWNER ninguno es el ultimo', () => {
    const a = member('a', 'OWNER')
    expect(isLastOwner([a, member('b', 'OWNER')], a)).toBe(false)
  })

  it('un no-OWNER nunca es el ultimo OWNER', () => {
    const b = member('b', 'EDITOR')
    expect(isLastOwner([member('a', 'OWNER'), b], b)).toBe(false)
  })
})
