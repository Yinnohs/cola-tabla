import { Link, useNavigate, useParams } from 'react-router'
import { CreateBoardForm, useBoards } from '../../features/boards'
import { MembersPanel, useWorkspace } from '../../features/workspaces'
import { canEdit, workspaceId, type WorkspaceId } from '../../shared/kernel'
import { AppError, isForbidden } from '../../shared/lib'
import { AccessDenied } from '../../shared/ui'

export function WorkspacePage() {
  const { workspaceId: raw } = useParams()
  if (!raw) return <p>Workspace no encontrado</p>
  return <WorkspaceContent id={workspaceId(raw)} />
}

function WorkspaceContent({ id }: { id: WorkspaceId }) {
  const navigate = useNavigate()
  const workspace = useWorkspace(id)

  if (workspace.isPending) return <p role="status">Cargando workspace...</p>
  if (workspace.isError) {
    if (isForbidden(workspace.error)) {
      return (
        <AccessDenied>
          <Link to="/">Volver a workspaces</Link>
        </AccessDenied>
      )
    }
    const notFound = workspace.error instanceof AppError && workspace.error.status === 404
    return (
      <section>
        <p role="alert">
          {notFound
            ? 'Workspace no encontrado'
            : `No se pudo cargar el workspace: ${workspace.error.message}`}
        </p>
        <Link to="/">Volver a workspaces</Link>
      </section>
    )
  }

  return (
    <section>
      <p>
        <Link to="/">Workspaces</Link>
      </p>
      <h1>{workspace.data.name}</h1>
      <BoardsOfWorkspace id={id} />
      {canEdit(workspace.data.role) && (
        <CreateBoardForm workspaceId={id} onCreated={(b) => void navigate(`/boards/${b.id}`)} />
      )}
      <MembersPanel workspaceId={id} />
    </section>
  )
}

function BoardsOfWorkspace({ id }: { id: WorkspaceId }) {
  const { data, isPending, isError, error } = useBoards(id)
  if (isPending) return <p role="status">Cargando tableros...</p>
  if (isError) return <p role="alert">No se pudieron cargar los tableros: {error.message}</p>
  if (data.length === 0) return <p>Este workspace aún no tiene tableros. Crea el primero.</p>
  return (
    <ul aria-label="Tableros">
      {data.map((b) => (
        <li key={b.id}>
          <Link to={`/boards/${b.id}`}>{b.name}</Link>
        </li>
      ))}
    </ul>
  )
}
