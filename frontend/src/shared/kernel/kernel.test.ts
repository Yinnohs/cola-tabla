import { describe, expect, it } from 'vitest'
import { err, flatMap, isErr, isOk, map, ok, unwrapOr, type Result } from './result'
import { boardId, cardId, type BoardId, type CardId } from './ids'

describe('Result', () => {
  it('ok/err discriminan', () => {
    expect(isOk(ok(1))).toBe(true)
    expect(isErr(err('x'))).toBe(true)
  })
  it('map solo transforma ok', () => {
    expect(map(ok(2), (n) => n * 2)).toEqual(ok(4))
    const e: Result<number, string> = err('boom')
    expect(map(e, (n) => n * 2)).toEqual(err('boom'))
  })
  it('flatMap encadena y cortocircuita', () => {
    const half = (n: number): Result<number, string> => (n % 2 === 0 ? ok(n / 2) : err('odd'))
    expect(flatMap(ok(4), half)).toEqual(ok(2))
    expect(flatMap(ok(3), half)).toEqual(err('odd'))
  })
  it('unwrapOr', () => {
    expect(unwrapOr(ok(1), 0)).toBe(1)
    expect(unwrapOr(err('x') as Result<number, string>, 0)).toBe(0)
  })
})

describe('ids tipados', () => {
  it('construye ids y rechaza vacíos', () => {
    expect(boardId('b1')).toBe('b1')
    expect(() => cardId('  ')).toThrow()
  })
  it('no son intercambiables en compilación', () => {
    const b: BoardId = boardId('x')
    // @ts-expect-error BoardId no es asignable a CardId
    const c: CardId = b
    expect(c).toBe('x')
  })
})
