import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Role } from '../../../shared/kernel'
import { AppError } from '../../../shared/lib'
import {
  InMemoryMemberRepository,
  InMemoryWorkspaceRepository,
  aMember,
  aWorkspace,
  renderWithPorts,
} from '../../../shared/testing'
import { MembersPanel } from './MembersPanel'

function setup(role: Role = 'OWNER') {
  const workspace = aWorkspace({ role })
  const owner = aMember({ workspaceId: workspace.id, userId: 'user-1', role: 'OWNER' })
  const viewer = aMember({ workspaceId: workspace.id, userId: 'vera', role: 'VIEWER' })
  const members = new InMemoryMemberRepository([owner, viewer])
  renderWithPorts(<MembersPanel workspaceId={workspace.id} />, {
    ports: { workspaces: new InMemoryWorkspaceRepository([workspace]), members },
  })
  return { workspace, members, user: userEvent.setup() }
}

describe('MembersPanel', () => {
  it('lista los miembros con su rol y marca a la persona actual', async () => {
    setup()
    const list = await screen.findByRole('list', { name: 'Lista de miembros' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(within(list).getByText(/user-1 \(tú\)/)).toBeInTheDocument()
    expect(screen.getByLabelText('Rol de vera')).toHaveValue('VIEWER')
  })

  it.each<Role>(['EDITOR', 'VIEWER'])('%s no ve la gestion de miembros', async (role) => {
    const { members } = setup(role)
    await new Promise((r) => setTimeout(r, 30))
    expect(screen.queryByRole('region', { name: 'Miembros' })).not.toBeInTheDocument()
    expect(members.items).toHaveLength(2)
  })

  it('invita por userId con un rol y limpia el campo', async () => {
    const { members, workspace, user } = setup()
    await user.type(await screen.findByLabelText('Identificador del usuario'), '  kc-sub-9 ')
    await user.selectOptions(screen.getByLabelText('Rol del invitado'), 'EDITOR')
    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    expect(await screen.findByLabelText('Rol de kc-sub-9')).toHaveValue('EDITOR')
    expect(members.items.find((m) => m.userId === 'kc-sub-9')).toMatchObject({
      workspaceId: workspace.id,
      role: 'EDITOR',
    })
    expect(screen.getByLabelText('Identificador del usuario')).toHaveValue('')
  })

  it('no permite invitar con el identificador vacio', async () => {
    const { members, user } = setup()
    await user.click(await screen.findByRole('button', { name: 'Invitar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('obligatorio')
    expect(members.items).toHaveLength(2)
  })

  it('invitar a alguien que ya es miembro muestra el error', async () => {
    const { user } = setup()
    await user.type(await screen.findByLabelText('Identificador del usuario'), 'vera')
    await user.click(screen.getByRole('button', { name: 'Invitar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('ya es miembro')
  })

  it('cambia el rol de un miembro', async () => {
    const { members, user } = setup()
    await user.selectOptions(await screen.findByLabelText('Rol de vera'), 'EDITOR')
    await waitFor(() => expect(members.items.find((m) => m.userId === 'vera')?.role).toBe('EDITOR'))
    await waitFor(() => expect(screen.getByLabelText('Rol de vera')).toHaveValue('EDITOR'))
  })

  it('quita a un miembro', async () => {
    const { members, user } = setup()
    await user.click(await screen.findByRole('button', { name: 'Quitar a vera' }))
    await waitFor(() => expect(members.items.map((m) => m.userId)).toEqual(['user-1']))
    await waitFor(() => expect(screen.queryByLabelText('Rol de vera')).not.toBeInTheDocument())
  })

  it('el ultimo OWNER no se puede quitar ni degradar desde la UI', async () => {
    setup()
    expect(await screen.findByRole('button', { name: 'Quitar a user-1' })).toBeDisabled()
    expect(screen.getByLabelText('Rol de user-1')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Quitar a vera' })).toBeEnabled()
  })

  it('si el backend responde LAST_OWNER se muestra el mensaje en espanol', async () => {
    const { members, user } = setup()
    await screen.findByLabelText('Rol de vera')
    members.failNextWith = new AppError('last owner', 409, 'LAST_OWNER')
    await user.selectOptions(screen.getByLabelText('Rol de vera'), 'EDITOR')
    expect(await screen.findByRole('alert')).toHaveTextContent('al menos un propietario')
  })
})
