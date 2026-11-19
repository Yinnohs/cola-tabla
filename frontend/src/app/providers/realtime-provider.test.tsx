import { describe, expect, it } from 'vitest'
import { act, screen } from '@testing-library/react'
import { boardId } from '../../shared/kernel'
import { FakeBoardEvents, renderWithPorts } from '../../shared/testing'
import { RealtimeProvider } from './RealtimeProvider'
import { useRealtimeStatus } from './realtime-status'

const B = boardId('11111111-1111-4111-8111-111111111111')

function Probe() {
  return <p>estado: {useRealtimeStatus()}</p>
}

describe('RealtimeProvider', () => {
  it('suscribe el tablero al montar, expone el estado y se da de baja al desmontar', () => {
    const events = new FakeBoardEvents()
    const { unmount } = renderWithPorts(
      <RealtimeProvider boardId={B}>
        <Probe />
      </RealtimeProvider>,
      { ports: { events } },
    )
    expect(events.activeSubscriptions).toHaveLength(1)
    expect(screen.getByText('estado: connecting')).toBeInTheDocument()

    act(() => events.setStatus(B, 'connected'))
    expect(screen.getByText('estado: connected')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    unmount()
    expect(events.activeSubscriptions).toHaveLength(0)
  })

  it('muestra el aviso "sin acceso" si el servidor rechaza la suscripcion', () => {
    const events = new FakeBoardEvents()
    renderWithPorts(
      <RealtimeProvider boardId={B}>
        <Probe />
      </RealtimeProvider>,
      { ports: { events } },
    )
    act(() => events.setStatus(B, 'access-denied'))
    expect(screen.getByRole('alert')).toHaveTextContent(/sin acceso/i)
  })
})
