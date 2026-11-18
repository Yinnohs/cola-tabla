import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  aBoard,
  aBoardDetails,
  aCardView,
  aListView,
  fakeBoardPorts,
  renderWithPorts,
} from '../../../shared/testing'
import { BoardScreen } from '../../boards'
import { BoardView } from './BoardView'

function setup() {
  const todo = aListView({ name: 'Todo', position: 'G' as never })
  const a = aCardView({ title: 'Tarea A', listId: todo.id })
  const details = aBoardDetails({ board: aBoard(), lists: [{ ...todo, cards: [a] }] })
  const ports = fakeBoardPorts(details)
  const boardId = details.board.id
  renderWithPorts(
    <BoardScreen boardId={boardId}>
      {(board) => <BoardView boardId={boardId} board={board} />}
    </BoardScreen>,
    { ports },
  )
  return { todo, ports, user: userEvent.setup() }
}

describe('BoardView', () => {
  it('muestra listas con sus tarjetas', async () => {
    setup()
    expect(await screen.findByRole('heading', { name: 'Todo', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('Tarea A')).toBeInTheDocument()
  })

  it('crea una lista con el formulario inline', async () => {
    const { ports, user } = setup()
    await user.type(await screen.findByLabelText('Nombre de la lista'), 'Doing')
    await user.click(screen.getByRole('button', { name: 'Añadir lista' }))
    expect(await screen.findByRole('heading', { name: 'Doing', level: 2 })).toBeInTheDocument()
    expect(ports.lists.items.map((l) => l.name)).toContain('Doing')
  })

  it('crea una tarjeta en una lista', async () => {
    const { ports, todo, user } = setup()
    await user.click(await screen.findByRole('button', { name: /Añadir tarjeta/ }))
    await user.type(screen.getByLabelText('Título de la tarjeta'), 'Nueva tarjeta')
    await user.click(screen.getByRole('button', { name: 'Añadir' }))
    expect(await screen.findByText('Nueva tarjeta')).toBeInTheDocument()
    expect(ports.cards.items.some((c) => c.title === 'Nueva tarjeta' && c.listId === todo.id)).toBe(
      true,
    )
  })

  it('no permite crear una lista con nombre vacio', async () => {
    const { ports, user } = setup()
    await user.click(await screen.findByRole('button', { name: 'Añadir lista' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El nombre es obligatorio')
    expect(ports.lists.items).toHaveLength(1)
  })

  it('renombra una lista', async () => {
    const { ports, todo, user } = setup()
    await user.click(await screen.findByRole('button', { name: 'Renombrar lista Todo' }))
    const input = screen.getByLabelText('Nuevo nombre de lista')
    await user.clear(input)
    await user.type(input, 'Por hacer{Enter}')
    expect(await screen.findByRole('heading', { name: 'Por hacer', level: 2 })).toBeInTheDocument()
    await waitFor(() =>
      expect(ports.lists.items.find((l) => l.id === todo.id)).toMatchObject({
        name: 'Por hacer',
        version: 1,
      }),
    )
  })
})
