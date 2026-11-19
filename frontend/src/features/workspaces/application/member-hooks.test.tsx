import { describe, expect, it } from 'vitest'
import { act, waitFor } from '@testing-library/react'
import { AppError } from '../../../shared/lib'
import {
  InMemoryMemberRepository,
  aMember,
  aWorkspace,
  createTestQueryClient,
  renderHookWithPorts,
} from '../../../shared/testing'
import { memberErrorMessage, InvalidMemberUserIdError } from './member-errors'
import { useChangeMemberRole, useInviteMember, useRemoveMember } from './use-member-mutations'
import { useMembers } from './use-members'

function setup() {
  const ws = aWorkspace()
  const owner = aMember({ workspaceId: ws.id, userId: 'owner', role: 'OWNER' })
  const members = new InMemoryMemberRepository([owner])
  const queryClient = createTestQueryClient()
  const view = renderHookWithPorts(
    () => ({
      list: useMembers(ws.id),
      invite: useInviteMember(ws.id),
      change: useChangeMemberRole(ws.id),
      remove: useRemoveMember(ws.id),
    }),
    { ports: { members }, queryClient },
  )
  return { ws, members, view }
}

describe('hooks de miembros', () => {
  it('useMembers carga la lista del puerto', async () => {
    const { view } = setup()
    await waitFor(() => expect(view.result.current.list.data).toHaveLength(1))
  })

  it('invitar valida el userId en dominio y refresca la lista', async () => {
    const { view, members } = setup()
    await waitFor(() => expect(view.result.current.list.isSuccess).toBe(true))
    await act(async () => {
      await view.result.current.invite.mutateAsync({ userId: '  ', role: 'VIEWER' }).catch(() => {})
    })
    await waitFor(() =>
      expect(view.result.current.invite.error).toBeInstanceOf(InvalidMemberUserIdError),
    )
    expect(members.items).toHaveLength(1)

    await act(async () => {
      await view.result.current.invite.mutateAsync({ userId: ' nuevo ', role: 'VIEWER' })
    })
    await waitFor(() => expect(view.result.current.list.data).toHaveLength(2))
    expect(members.items.map((m) => m.userId)).toContain('nuevo')
  })

  it('cambiar rol y quitar actualizan la lista; el ultimo OWNER falla con LAST_OWNER', async () => {
    const { view, members } = setup()
    await waitFor(() => expect(view.result.current.list.isSuccess).toBe(true))
    await act(async () => {
      await view.result.current.invite.mutateAsync({ userId: 'v', role: 'VIEWER' })
    })
    await act(async () => {
      await view.result.current.change.mutateAsync({ userId: 'v', role: 'EDITOR' })
    })
    expect(members.items.find((m) => m.userId === 'v')?.role).toBe('EDITOR')
    await act(async () => {
      await view.result.current.remove.mutateAsync('v')
    })
    await waitFor(() => expect(view.result.current.list.data).toHaveLength(1))

    await act(async () => {
      await view.result.current.remove.mutateAsync('owner').catch(() => {})
    })
    await waitFor(() =>
      expect(view.result.current.remove.error).toMatchObject({ code: 'LAST_OWNER' }),
    )
  })
})

describe('memberErrorMessage', () => {
  it('traduce los codigos del backend', () => {
    expect(memberErrorMessage(new AppError('x', 409, 'LAST_OWNER'))).toMatch(
      /al menos un propietario/,
    )
    expect(memberErrorMessage(new AppError('x', 409, 'ALREADY_MEMBER'))).toMatch(/ya es miembro/)
    expect(memberErrorMessage(new AppError('x', 403, 'FORBIDDEN'))).toMatch(/No tienes permiso/)
    expect(memberErrorMessage(new Error('otro'))).toBe('otro')
  })
})
