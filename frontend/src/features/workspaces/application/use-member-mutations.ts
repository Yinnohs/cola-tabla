import { useMutation, useQueryClient } from '@tanstack/react-query'
import { usePorts } from '../../../app/di'
import type { Role, WorkspaceId } from '../../../shared/kernel'
import { memberUserId } from '../domain/member'
import { InvalidMemberUserIdError } from './member-errors'
import { workspaceKeys } from './query-keys'

function useInvalidateMembers(workspaceId: WorkspaceId) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspaceId) })
}

/** Invita por userId (sub de Keycloak) con un rol. */
export function useInviteMember(workspaceId: WorkspaceId) {
  const { members } = usePorts()
  const invalidate = useInvalidateMembers(workspaceId)
  return useMutation({
    mutationFn: async (input: { userId: string; role: Role }) => {
      const valid = memberUserId(input.userId)
      if (!valid.ok) throw new InvalidMemberUserIdError(valid.error)
      return members.invite(workspaceId, valid.value, input.role)
    },
    onSuccess: invalidate,
  })
}

export function useChangeMemberRole(workspaceId: WorkspaceId) {
  const { members } = usePorts()
  const invalidate = useInvalidateMembers(workspaceId)
  return useMutation({
    mutationFn: (input: { userId: string; role: Role }) =>
      members.changeRole(workspaceId, input.userId, input.role),
    onSettled: invalidate,
  })
}

export function useRemoveMember(workspaceId: WorkspaceId) {
  const { members } = usePorts()
  const invalidate = useInvalidateMembers(workspaceId)
  return useMutation({
    mutationFn: (userId: string) => members.remove(workspaceId, userId),
    onSettled: invalidate,
  })
}
