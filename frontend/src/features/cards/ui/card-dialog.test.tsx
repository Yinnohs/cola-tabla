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
  const a = aCardView({ title: 'Tarea A', description: 'desc A', listId: todo.id })
  const details = aBoardDetails({ board: aBoard(), lists: [{ ...todo, cards: [a] }] })
  const ports = fakeBoardPorts(details)
  const boardId = details.board.id
  renderWithPorts(
    <BoardScreen boardId={boardId}>
      {(board) => <BoardView boardId={boardId} board={board} />}
    </BoardScreen>,
    { ports },
  )
  return { a, ports, user: userEvent.setup() }
}

async function openCard(user: ReturnType<typeof userEvent.setup>, title: string) {
  await user.click(await screen.findByText(title))
  return screen.findByRole('dialog', { name: 'Editar tarjeta' })
}

describe('CardDialog', () => {
  it('edita titulo y descripcion y guarda (con la version actual)', async () => {
    const { a, ports, user } = setup()
    await openCard(user, 'Tarea A')
    const title = screen.getByLabelText('Título')
    expect(title).toHaveValue('Tarea A')
    expect(screen.getByLabelText('Descripción')).toHaveValue('desc A')
    await user.clear(title)
    await user.type(title, 'Tarea A2')
    await user.clear(screen.getByLabelText('Descripción'))
    await user.type(screen.getByLabelText('Descripción'), 'nueva')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(ports.cards.items.find((c) => c.id === a.id)).toMatchObject({
      title: 'Tarea A2',
      description: 'nueva',
      version: 1,
    })
    expect(await screen.findByText('Tarea A2')).toBeInTheDocument()
  })

  it('con el teclado: Enter abre, Escape cierra y devuelve el foco a la tarjeta', async () => {
    const { user } = setup()
    const card = (await screen.findAllByRole('button', { name: /Tarea A/ }))[0]!
    card.focus()
    await user.keyboard('{Enter}')
    expect(await screen.findByRole('dialog', { name: 'Editar tarjeta' })).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(card).toHaveFocus()
  })

  it('muestra el conflicto STALE_VERSION y permite recargar los datos vigentes', async () => {
    const { a, ports, user } = setup()
    await openCard(user, 'Tarea A')
    // otra persona edita la tarjeta mientras el dialogo esta abierto
    ports.cards.editRemotely(a.id, { title: 'Editada por otra persona' })
    const title = screen.getByLabelText('Título')
    await user.clear(title)
    await user.type(title, 'Mi cambio')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('ha sido modificada por otra persona')
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled()
    // el servidor conserva el cambio ajeno
    expect(ports.cards.items.find((c) => c.id === a.id)?.title).toBe('Editada por otra persona')

    await user.click(screen.getByRole('button', { name: 'Recargar datos actuales' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Título')).toHaveValue('Editada por otra persona'),
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Título'), ' + yo')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(ports.cards.items.find((c) => c.id === a.id)?.title).toBe(
      'Editada por otra persona + yo',
    )
  })

  it('elimina la tarjeta tras confirmar', async () => {
    const { ports, user } = setup()
    await openCard(user, 'Tarea A')
    await user.click(screen.getByRole('button', { name: 'Eliminar tarjeta' }))
    expect(ports.cards.items).toHaveLength(1) // aun no: pide confirmacion
    await user.click(screen.getByRole('button', { name: 'Confirmar eliminación' }))
    await waitFor(() => expect(ports.cards.items).toHaveLength(0))
    await waitFor(() => expect(screen.queryByText('Tarea A')).not.toBeInTheDocument())
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('un titulo vacio muestra el error y no llama al servidor', async () => {
    const { a, ports, user } = setup()
    await openCard(user, 'Tarea A')
    await user.clear(screen.getByLabelText('Título'))
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('El titulo es obligatorio')
    expect(ports.cards.items.find((c) => c.id === a.id)?.version).toBe(0)
  })
})
