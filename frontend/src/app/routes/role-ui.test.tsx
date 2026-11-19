import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { AppError } from '../../shared/lib'
import type { Role } from '../../shared/kernel'
import {
  FakeAuthPort,
  InMemoryMemberRepository,
  InMemoryWorkspaceRepository,
  SIGNED_OUT,
  aBoard,
  aBoardDetails,
  aCardView,
  aListView,
  aMember,
  aWorkspace,
  fakeBoardPorts,
  renderWithPorts,
} from '../../shared/testing'
import { AppRoutes } from './AppRoutes'

function boardFixture(role: Role) {
  const workspace = aWorkspace({ name: 'Equipo', role })
  const list = aListView({ name: 'Todo', position: 'G' as never })
  const card = aCardView({ title: 'Tarea A', description: 'Detalle', listId: list.id })
  const details = aBoardDetails({
    board: aBoard({ name: 'Sprint', workspaceId: workspace.id }),
    lists: [{ ...list, cards: [card] }],
  })
  const ports = fakeBoardPorts(details)
  return { workspace, details, ports }
}

function renderBoard(role: Role) {
  const f = boardFixture(role)
  renderWithPorts(
    <MemoryRouter initialEntries={[`/boards/${f.details.board.id}`]}>
      <AppRoutes />
    </MemoryRouter>,
    { ports: { ...f.ports, workspaces: new InMemoryWorkspaceRepository([f.workspace]) } },
  )
  return f
}

describe('UI de tablero segun rol (F2.6)', () => {
  it('VIEWER: solo lectura, sin controles de edicion y sin DndContext', async () => {
    renderBoard('VIEWER')
    expect(await screen.findByRole('heading', { name: 'Todo', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('Tarea A')).toBeInTheDocument()
    expect(screen.getByText(/Solo lectura/)).toBeInTheDocument()

    expect(screen.queryByRole('button', { name: /Renombrar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Añadir tarjeta/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Crear lista' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Mover lista/ })).not.toBeInTheDocument()
    // BoardDnd (unico que monta DndContext) pinta la region de estado del arrastre y las instrucciones.
    expect(screen.queryByLabelText('Estado del arrastre')).not.toBeInTheDocument()
    expect(document.querySelector('[id^="DndDescribedBy"]')).toBeNull()
    expect(document.querySelector('[aria-roledescription="sortable"]')).toBeNull()
  })

  it('VIEWER puede abrir una tarjeta pero solo para leerla', async () => {
    renderBoard('VIEWER')
    await userEvent.click(await screen.findByRole('button', { name: /Tarea A/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Tarjeta')
    expect(screen.getByLabelText('Título')).toHaveAttribute('readonly')
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Eliminar tarjeta' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it.each<Role>(['EDITOR', 'OWNER'])('%s: edita y tiene drag & drop', async (role) => {
    renderBoard(role)
    expect(await screen.findByRole('heading', { name: 'Todo', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Renombrar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Añadir tarjeta/ })).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Crear lista' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Mover lista/ })).toBeInTheDocument()
    expect(screen.getByLabelText('Estado del arrastre')).toBeInTheDocument()
    expect(screen.queryByText(/Solo lectura/)).not.toBeInTheDocument()
  })
})

function renderWorkspace(role: Role, members = new InMemoryMemberRepository()) {
  const workspace = aWorkspace({ name: 'Equipo', role })
  members.items.push(aMember({ workspaceId: workspace.id, userId: 'user-1', role: 'OWNER' }))
  renderWithPorts(
    <MemoryRouter initialEntries={[`/workspaces/${workspace.id}`]}>
      <AppRoutes />
    </MemoryRouter>,
    { ports: { workspaces: new InMemoryWorkspaceRepository([workspace]), members } },
  )
}

describe('UI de workspace segun rol', () => {
  it('OWNER ve crear tablero y la gestion de miembros', async () => {
    renderWorkspace('OWNER')
    expect(await screen.findByRole('form', { name: 'Crear tablero' })).toBeInTheDocument()
    expect(await screen.findByRole('region', { name: 'Miembros' })).toBeInTheDocument()
  })

  it('EDITOR crea tableros pero no ve miembros', async () => {
    renderWorkspace('EDITOR')
    expect(await screen.findByRole('form', { name: 'Crear tablero' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Miembros' })).not.toBeInTheDocument()
  })

  it('VIEWER no ve crear tablero ni miembros', async () => {
    renderWorkspace('VIEWER')
    expect(await screen.findByText(/aún no tiene tableros/)).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Crear tablero' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Miembros' })).not.toBeInTheDocument()
  })
})

describe('403 y rutas protegidas', () => {
  it('403 al cargar un workspace muestra la pantalla de sin permiso', async () => {
    const repo = new InMemoryWorkspaceRepository()
    repo.failNextWith = new AppError('forbidden', 403, 'FORBIDDEN')
    renderWithPorts(
      <MemoryRouter initialEntries={['/workspaces/00000000-0000-4000-8000-0000000000aa']}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { workspaces: repo } },
    )
    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver a workspaces' })).toBeInTheDocument()
  })

  it('403 al cargar un tablero muestra la pantalla de sin permiso', async () => {
    const f = boardFixture('VIEWER')
    f.ports.boards.get = () => Promise.reject(new AppError('forbidden', 403, 'FORBIDDEN'))
    renderWithPorts(
      <MemoryRouter initialEntries={[`/boards/${f.details.board.id}`]}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { ...f.ports, workspaces: new InMemoryWorkspaceRepository([f.workspace]) } },
    )
    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
  })

  it('sin sesion cualquier ruta privada redirige al login con su ruta de retorno', async () => {
    const auth = new FakeAuthPort(SIGNED_OUT)
    renderWithPorts(
      <MemoryRouter initialEntries={['/workspaces/abc']}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { auth } },
    )
    expect(await screen.findByText(/Redirigiendo al inicio de sesión/)).toBeInTheDocument()
    await screen.findByRole('status')
    expect(auth.loginCalls).toEqual(['/workspaces/abc'])
  })

  it('las rutas de auth son publicas', async () => {
    renderWithPorts(
      <MemoryRouter initialEntries={['/auth/signed-out']}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { auth: new FakeAuthPort(SIGNED_OUT) } },
    )
    expect(await screen.findByRole('heading', { name: 'Sesión cerrada' })).toBeInTheDocument()
  })
})
