import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { usePorts } from '../../app/di'
import { fakeAuthPort, renderHookWithPorts, renderWithPorts } from './index'

function Token() {
  return <span>{usePorts().auth.getToken()}</span>
}

describe('render con fakes', () => {
  it('inyecta puertos fake en componentes', () => {
    renderWithPorts(<Token />, { ports: { auth: fakeAuthPort('abc') } })
    expect(screen.getByText('abc')).toBeInTheDocument()
  })

  it('inyecta puertos fake en hooks', () => {
    const { result } = renderHookWithPorts(() => usePorts().auth.getToken())
    expect(result.current).toBe('test-token')
  })

  it('usePorts falla fuera del provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Token />)).toThrow(/PortsProvider/)
    vi.restoreAllMocks()
  })
})
