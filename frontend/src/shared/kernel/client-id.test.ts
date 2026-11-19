import { describe, expect, it } from 'vitest'
import fc from 'fast-check'
import { isClientId, toClientId } from './client-id'

describe('clientId', () => {
  it('acepta un uuid tal cual', () => {
    const id = '0b6d8d1e-7d0f-4b0d-8a6e-6a4c5f3a0002'
    expect(toClientId(id)).toBe(id)
    expect(isClientId(id)).toBe(true)
  })

  it('sustituye caracteres invalidos y recorta a 64', () => {
    expect(toClientId('a b/c')).toBe('a-b-c')
    expect(toClientId('x'.repeat(100))).toHaveLength(64)
  })

  it('rechaza vacio y valores no validos', () => {
    expect(() => toClientId('')).toThrow()
    expect(isClientId('')).toBe(false)
    expect(isClientId('con espacio')).toBe(false)
    expect(isClientId('x'.repeat(65))).toBe(false)
    expect(isClientId(42)).toBe(false)
  })

  it('propiedad: toda cadena no vacia produce un clientId valido', () => {
    fc.assert(fc.property(fc.string({ minLength: 1 }), (s) => isClientId(toClientId(s))))
  })
})
