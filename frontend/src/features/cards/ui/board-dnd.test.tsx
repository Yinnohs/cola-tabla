import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
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

/**
 * jsdom no hace layout: se simula uno. Listas en columnas de 300px; tarjetas apiladas de 50px dentro
 * de su lista. dnd-kit calcula con estos rectangulos a donde salta el teclado.
 */
function rectOf(el: Element): DOMRect {
  let x = 0
  let y = 0
  let width = 0
  let height = 0
  // El overlay de arrastre (DragOverlay) ocupa el sitio de la tarjeta que se arrastra.
  const dragging = document.querySelector('.card--dragging')
  if (dragging && el.classList.contains('card--overlay')) return rectOf(dragging)
  const slot = el.closest('.list-slot')
  if (el.classList.contains('list-slot')) {
    x = Array.from(el.parentElement!.children).indexOf(el) * 300
    width = 280
    height = 600
  } else if (slot && el.classList.contains('card')) {
    const column = Array.from(slot.parentElement!.children).indexOf(slot)
    const row = Array.from(el.parentElement!.children).indexOf(el)
    x = column * 300 + 10
    y = 40 + row * 50
    width = 260
    height = 40
  }
  return {
    x,
    y,
    width,
    height,
    top: y,
    left: x,
    right: x + width,
    bottom: y + height,
    toJSON: () => ({}),
  } as DOMRect
}

beforeEach(() => {
  vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
    return rectOf(this)
  })
  Element.prototype.scrollIntoView = vi.fn()
})

afterEach(() => vi.restoreAllMocks())

function setup() {
  const todo = aListView({ name: 'Todo', position: 'G' as never })
  const doing = aListView({ name: 'Doing', position: 'V' as never })
  const a = aCardView({ title: 'A', listId: todo.id, position: 'G' as never })
  const b = aCardView({ title: 'B', listId: todo.id, position: 'V' as never })
  const c = aCardView({ title: 'C', listId: todo.id, position: 'k' as never })
  const details = aBoardDetails({
    board: aBoard({ name: 'Sprint' }),
    lists: [{ ...todo, cards: [a, b, c] }, doing],
  })
  const ports = fakeBoardPorts(details)
  const boardId = details.board.id
  renderWithPorts(
    <BoardScreen boardId={boardId}>
      {(board) => <BoardView boardId={boardId} board={board} />}
    </BoardScreen>,
    { ports },
  )
  const status = () => screen.getByRole('status', { name: 'Estado del arrastre' })
  const titles = (listName: string) =>
    within(screen.getByRole('list', { name: `Tarjetas de ${listName}` }))
      .queryAllByRole('listitem')
      .map((li) => li.textContent)
  return { todo, doing, a, b, c, ports, status, titles }
}

async function cardButton(title: string) {
  const buttons = await screen.findAllByRole('button', { name: title })
  return buttons[0]!
}

describe('drag & drop solo con teclado', () => {
  it('mueve una tarjeta dentro de la lista: Espacio, Flecha abajo, Espacio', async () => {
    const s = setup()
    const user = userEvent.setup()
    const card = await cardButton('A')
    card.focus()
    await user.keyboard(' ')
    await waitFor(() =>
      expect(s.status()).toHaveTextContent(
        /Tarjeta "A" recogida en la lista "Todo", posición 1 de 3/,
      ),
    )
    await user.keyboard('{ArrowDown}')
    await waitFor(() =>
      expect(s.status()).toHaveTextContent('Tarjeta "A" en la lista "Todo", posición 2 de 3.'),
    )
    await user.keyboard(' ')

    await waitFor(() => expect(s.ports.cards.moves).toHaveLength(1))
    expect(s.ports.cards.moves[0]).toEqual({
      id: s.a.id,
      listId: s.todo.id,
      prevCardId: s.b.id,
      nextCardId: s.c.id,
    })
    expect(s.status()).toHaveTextContent('Tarjeta "A" soltada en la lista "Todo", posición 2 de 3.')
    await waitFor(() => expect(s.titles('Todo')).toEqual(['B', 'A', 'C']))
  })

  it('mueve una tarjeta a otra lista (vacia) con Flecha derecha', async () => {
    const s = setup()
    const user = userEvent.setup()
    const card = await cardButton('B')
    card.focus()
    await user.keyboard(' ')
    await user.keyboard('{ArrowRight}')
    await user.keyboard(' ')

    await waitFor(() => expect(s.ports.cards.moves).toHaveLength(1))
    expect(s.ports.cards.moves[0]).toEqual({
      id: s.b.id,
      listId: s.doing.id,
      prevCardId: null,
      nextCardId: null,
    })
    expect(s.status()).toHaveTextContent(
      'Tarjeta "B" soltada en la lista "Doing", posición 1 de 1.',
    )
    await waitFor(() => expect(s.titles('Doing')).toEqual(['B']))
    expect(s.titles('Todo')).toEqual(['A', 'C'])
  })

  it('Escape cancela el arrastre y no llama al servidor', async () => {
    const s = setup()
    const user = userEvent.setup()
    const card = await cardButton('A')
    card.focus()
    await user.keyboard(' ')
    await user.keyboard('{ArrowDown}')
    await user.keyboard('{Escape}')
    await waitFor(() =>
      expect(s.status()).toHaveTextContent('Movimiento cancelado. Tarjeta "A" vuelve a su sitio.'),
    )
    expect(s.ports.cards.moves).toEqual([])
    expect(s.titles('Todo')).toEqual(['A', 'B', 'C'])
  })

  it('reordena listas con el asa: Espacio, Flecha derecha, Espacio', async () => {
    const s = setup()
    const user = userEvent.setup()
    const handle = await screen.findByRole('button', { name: 'Mover lista Todo' })
    handle.focus()
    await user.keyboard(' ')
    await waitFor(() =>
      expect(s.status()).toHaveTextContent(/Lista "Todo" recogida, posición 1 de 2/),
    )
    await user.keyboard('{ArrowRight}')
    await user.keyboard(' ')

    await waitFor(() => expect(s.ports.lists.moves).toHaveLength(1))
    expect(s.ports.lists.moves[0]).toEqual({
      id: s.todo.id,
      prevListId: s.doing.id,
      nextListId: null,
    })
    expect(s.status()).toHaveTextContent('Lista "Todo" soltada en la posición 2 de 2.')
    await waitFor(() => {
      const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
      expect(headings).toEqual(['Doing', 'Todo'])
    })
  })

  it('Enter abre la tarjeta (no inicia el arrastre)', async () => {
    setup()
    const user = userEvent.setup()
    const card = await cardButton('A')
    card.focus()
    await user.keyboard('{Enter}')
    expect(await screen.findByRole('dialog', { name: 'Editar tarjeta' })).toBeInTheDocument()
  })
})
