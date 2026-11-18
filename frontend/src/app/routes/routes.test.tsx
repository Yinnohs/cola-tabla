import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import {
  InMemoryBoardRepository,
  InMemoryWorkspaceRepository,
  aBoard,
  aBoardDetails,
  aWorkspace,
  renderWithPorts,
} from '../../shared/testing'
import type { Ports } from '../di'
import { AppRoutes } from './AppRoutes'

describe('routing workspace -> board', () => {
  function renderAt(path: string, ports: Partial<Ports>) {
    return renderWithPorts(
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>,
      { ports },
    )
  }

  it('lista workspaces, crea un tablero y navega a el', async () => {
    const ws = aWorkspace({ name: 'Equipo' })
    const boards = new InMemoryBoardRepository()
    renderWithPorts(
      <MemoryRouter initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { workspaces: new InMemoryWorkspaceRepository([ws]), boards } },
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Equipo' }))
    await userEvent.type(await screen.findByLabelText('Nombre del tablero'), 'Sprint 1')
    await userEvent.click(screen.getByRole('button', { name: 'Crear tablero' }))
    expect(await screen.findByRole('heading', { name: /Sprint 1/ })).toBeInTheDocument()
  })

  it('el tablero muestra su nombre como titulo y enlaza de vuelta al workspace', async () => {
    const board = aBoard({ name: 'Directo' })
    renderAt(`/boards/${board.id}`, {
      boards: new InMemoryBoardRepository([aBoardDetails({ board })]),
    })
    expect(await screen.findByRole('heading', { level: 1, name: /Directo/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al workspace' })).toHaveAttribute(
      'href',
      `/workspaces/${board.workspaceId}`,
    )
  })

  it('abre un tablero por URL', async () => {
    const board = aBoard({ name: 'Directo' })
    renderWithPorts(
      <MemoryRouter initialEntries={[`/boards/${board.id}`]}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { boards: new InMemoryBoardRepository([aBoardDetails({ board })]) } },
    )
    expect(await screen.findByRole('heading', { name: /Directo/ })).toBeInTheDocument()
  })
})

describe('WorkspacePage', () => {
  it('muestra el nombre del workspace como titulo y sus tableros como enlaces', async () => {
    const ws = aWorkspace({ name: 'Equipo Alfa' })
    const mine = aBoard({ workspaceId: ws.id, name: 'Sprint 1' })
    const other = aBoard({ name: 'Ajeno' })
    renderWithPorts(
      <MemoryRouter initialEntries={[`/workspaces/${ws.id}`]}>
        <AppRoutes />
      </MemoryRouter>,
      {
        ports: {
          workspaces: new InMemoryWorkspaceRepository([ws]),
          boards: new InMemoryBoardRepository([
            aBoardDetails({ board: mine }),
            aBoardDetails({ board: other }),
          ]),
        },
      },
    )
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Equipo Alfa' }),
    ).toBeInTheDocument()
    const link = await screen.findByRole('link', { name: 'Sprint 1' })
    expect(link).toHaveAttribute('href', `/boards/${mine.id}`)
    expect(screen.queryByRole('link', { name: 'Ajeno' })).not.toBeInTheDocument()
  })

  it('muestra un mensaje cuando no hay tableros', async () => {
    const ws = aWorkspace({ name: 'Vacio' })
    renderWithPorts(
      <MemoryRouter initialEntries={[`/workspaces/${ws.id}`]}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { workspaces: new InMemoryWorkspaceRepository([ws]) } },
    )
    expect(await screen.findByText(/aún no tiene tableros/)).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Crear tablero' })).toBeInTheDocument()
  })

  it('muestra el estado de carga y luego el 404 de un workspace inexistente', async () => {
    renderWithPorts(
      <MemoryRouter initialEntries={['/workspaces/00000000-0000-4000-8000-0000000000ff']}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { workspaces: new InMemoryWorkspaceRepository() } },
    )
    expect(screen.getByRole('status')).toHaveTextContent(/Cargando workspace/)
    expect(await screen.findByRole('alert')).toHaveTextContent('Workspace no encontrado')
    expect(screen.getByRole('link', { name: 'Volver a workspaces' })).toBeInTheDocument()
  })

  it('muestra el error generico si falla la carga', async () => {
    const repo = new InMemoryWorkspaceRepository()
    repo.failNextWith = new Error('boom')
    renderWithPorts(
      <MemoryRouter initialEntries={['/workspaces/00000000-0000-4000-8000-0000000000ff']}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { workspaces: repo } },
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(/boom/)
  })

  it('un tablero recien creado aparece en la lista al volver al workspace', async () => {
    const ws = aWorkspace({ name: 'Equipo' })
    renderWithPorts(
      <MemoryRouter initialEntries={[`/workspaces/${ws.id}`]}>
        <AppRoutes />
      </MemoryRouter>,
      { ports: { workspaces: new InMemoryWorkspaceRepository([ws]) } },
    )
    await userEvent.type(await screen.findByLabelText('Nombre del tablero'), 'Nuevo')
    await userEvent.click(screen.getByRole('button', { name: 'Crear tablero' }))
    expect(await screen.findByRole('heading', { name: /Nuevo/ })).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('link', { name: 'Volver al workspace' }))
    expect(await screen.findByRole('link', { name: 'Nuevo' })).toBeInTheDocument()
  })
})
