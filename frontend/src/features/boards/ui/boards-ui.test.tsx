import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  InMemoryBoardRepository,
  aBoard,
  aBoardDetails,
  aCardView,
  aListView,
  renderWithPorts,
} from '../../../shared/testing'
import { workspaceId } from '../../../shared/kernel'
import { BoardScreen } from './BoardScreen'
import { CreateBoardForm } from './CreateBoardForm'

describe('BoardScreen', () => {
  function setup() {
    const list = aListView({ name: 'Todo' })
    const card = aCardView({ title: 'Tarea 1', listId: list.id })
    const details = aBoardDetails({
      board: aBoard({ name: 'Sprint' }),
      lists: [{ ...list, cards: [card] }],
    })
    const repo = new InMemoryBoardRepository([details])
    renderWithPorts(<BoardScreen boardId={details.board.id} />, { ports: { boards: repo } })
    return { repo, details }
  }

  it('muestra nombre, listas y tarjetas', async () => {
    setup()
    expect(await screen.findByRole('heading', { name: /Sprint/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Todo' })).toBeInTheDocument()
    expect(screen.getByText('Tarea 1')).toBeInTheDocument()
  })

  it('renombra el tablero con el teclado', async () => {
    const { repo, details } = setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Renombrar' }))
    const input = screen.getByLabelText('Nuevo nombre')
    await userEvent.clear(input)
    await userEvent.type(input, 'Renombrado{Enter}')
    expect(await screen.findByRole('heading', { name: /Renombrado/ })).toBeInTheDocument()
    expect(repo.boards.get(details.board.id)?.board.name).toBe('Renombrado')
  })

  it('muestra error de carga', async () => {
    const repo = new InMemoryBoardRepository()
    renderWithPorts(<BoardScreen boardId={aBoard().id} />, { ports: { boards: repo } })
    expect(await screen.findByRole('alert')).toHaveTextContent(/not found/)
  })
})

describe('CreateBoardForm', () => {
  it('crea un tablero y avisa', async () => {
    const repo = new InMemoryBoardRepository()
    const onCreated = vi.fn()
    renderWithPorts(<CreateBoardForm workspaceId={workspaceId('w-1')} onCreated={onCreated} />, {
      ports: { boards: repo },
    })
    await userEvent.type(screen.getByLabelText('Nombre del tablero'), 'Roadmap')
    await userEvent.click(screen.getByRole('button', { name: 'Crear tablero' }))
    await waitFor(() => expect(onCreated).toHaveBeenCalled())
    expect(onCreated.mock.calls[0]?.[0]).toMatchObject({ name: 'Roadmap', workspaceId: 'w-1' })
  })
})
