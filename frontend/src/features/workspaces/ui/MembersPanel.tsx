import { useState, type FormEvent } from 'react'
import { useSession } from '../../auth'
import {
  ROLES,
  ROLE_LABELS,
  canAdmin,
  toRole,
  type Role,
  type WorkspaceId,
} from '../../../shared/kernel'
import { isLastOwner, type Member } from '../domain/member'
import { memberErrorMessage } from '../application/member-errors'
import {
  useChangeMemberRole,
  useInviteMember,
  useRemoveMember,
} from '../application/use-member-mutations'
import { useMembers } from '../application/use-members'
import { useWorkspace } from '../application/use-workspace'

function RoleSelect({
  label,
  value,
  onChange,
  disabled,
}: {
  label?: string
  value: Role
  onChange: (role: Role) => void
  disabled?: boolean
}) {
  return (
    <select
      aria-label={label}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(toRole(e.target.value))}
    >
      {ROLES.map((r) => (
        <option key={r} value={r}>
          {ROLE_LABELS[r]}
        </option>
      ))}
    </select>
  )
}

/** Gestion de miembros del workspace. Solo se muestra (y opera) para OWNER; el resto no ve nada. */
export function MembersPanel({ workspaceId }: { workspaceId: WorkspaceId }) {
  const workspace = useWorkspace(workspaceId)
  if (!workspace.data || !canAdmin(workspace.data.role)) return null
  return <MembersAdmin workspaceId={workspaceId} />
}

function MembersAdmin({ workspaceId }: { workspaceId: WorkspaceId }) {
  const members = useMembers(workspaceId)
  const invite = useInviteMember(workspaceId)
  const changeRole = useChangeMemberRole(workspaceId)
  const remove = useRemoveMember(workspaceId)
  const session = useSession()
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<Role>('VIEWER')
  const me = session.user?.id

  function submit(e: FormEvent) {
    e.preventDefault()
    invite.mutate({ userId, role }, { onSuccess: () => setUserId('') })
  }

  const error = invite.error ?? changeRole.error ?? remove.error

  return (
    <section aria-label="Miembros" className="members">
      <h2>Miembros</h2>
      {members.isPending && <p role="status">Cargando miembros...</p>}
      {members.isError && (
        <p role="alert">No se pudieron cargar los miembros: {memberErrorMessage(members.error)}</p>
      )}
      {members.data && (
        <ul aria-label="Lista de miembros">
          {members.data.map((m: Member) => {
            const last = isLastOwner(members.data, m)
            return (
              <li key={m.userId}>
                <span className="member-id">
                  {m.userId}
                  {m.userId === me ? ' (tú)' : ''}
                </span>
                <RoleSelect
                  label={`Rol de ${m.userId}`}
                  value={m.role}
                  disabled={changeRole.isPending || last}
                  onChange={(next) => changeRole.mutate({ userId: m.userId, role: next })}
                />
                <button
                  type="button"
                  className="btn-danger"
                  aria-label={`Quitar a ${m.userId}`}
                  disabled={remove.isPending || last}
                  title={last ? 'El workspace debe conservar al menos un propietario' : undefined}
                  onClick={() => remove.mutate(m.userId)}
                >
                  Quitar
                </button>
              </li>
            )
          })}
        </ul>
      )}
      <form onSubmit={submit} aria-label="Invitar miembro" className="inline-form">
        <label>
          Identificador del usuario
          <input value={userId} onChange={(e) => setUserId(e.target.value)} maxLength={255} />
        </label>
        <label>
          Rol del invitado
          <RoleSelect value={role} onChange={setRole} />
        </label>
        <button type="submit" disabled={invite.isPending}>
          Invitar
        </button>
      </form>
      {error && <p role="alert">{memberErrorMessage(error)}</p>}
    </section>
  )
}
