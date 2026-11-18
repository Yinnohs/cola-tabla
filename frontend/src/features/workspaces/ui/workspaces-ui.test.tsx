import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InMemoryWorkspaceRepository, aWorkspace, renderWithPorts } from '../../../shared/testing'
import { CreateWorkspaceForm } from './CreateWorkspaceForm'
import { WorkspaceList } from './WorkspaceList'

describe('WorkspaceList', () => {
  it('muestra estado vacio', async () => {
    renderWithPorts(<WorkspaceList onOpen={() => {}} />, {
      ports: { workspaces: new InMemoryWorkspaceRepository() },
    })
    expect(await screen.findByText(/no tienes workspaces/)).toBeInTheDocument()
  })

  it('lista y abre un workspace con teclado', async () => {
    const w = aWorkspace({ name: 'Equipo' })
    const onOpen = vi.fn()
    renderWithPorts(<WorkspaceList onOpen={onOpen} />, {
      ports: { workspaces: new InMemoryWorkspaceRepository([w]) },
    })
    const btn = await screen.findByRole('button', { name: 'Equipo' })
    btn.focus()
    await userEvent.keyboard('{Enter}')
    expect(onOpen).toHaveBeenCalledWith(w.id)
  })

  it('muestra el error de carga', async () => {
    const repo = new InMemoryWorkspaceRepository()
    repo.failNextWith = new Error('sin red')
    renderWithPorts(<WorkspaceList onOpen={() => {}} />, { ports: { workspaces: repo } })
    expect(await screen.findByRole('alert')).toHaveTextContent('sin red')
  })
})

describe('CreateWorkspaceForm', () => {
  it('crea un workspace y limpia el campo', async () => {
    const repo = new InMemoryWorkspaceRepository()
    const onCreated = vi.fn()
    renderWithPorts(<CreateWorkspaceForm onCreated={onCreated} />, { ports: { workspaces: repo } })
    const input = screen.getByLabelText('Nombre del workspace')
    await userEvent.type(input, 'Mi equipo')
    await userEvent.click(screen.getByRole('button', { name: 'Crear workspace' }))
    await waitFor(() => expect(onCreated).toHaveBeenCalled())
    expect(repo.items.map((w) => w.name)).toEqual(['Mi equipo'])
    expect(input).toHaveValue('')
  })

  it('muestra error de validacion con nombre vacio', async () => {
    renderWithPorts(<CreateWorkspaceForm />)
    await userEvent.click(screen.getByRole('button', { name: 'Crear workspace' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('obligatorio')
  })
})
