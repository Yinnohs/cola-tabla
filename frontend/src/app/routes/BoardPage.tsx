import { Link, useParams } from 'react-router'
import { BoardScreen, useBoard } from '../../features/boards'
import { BoardView } from '../../features/cards'
import { useWorkspace } from '../../features/workspaces'
import { boardId, canEdit, type BoardId, type WorkspaceId } from '../../shared/kernel'
import { isForbidden } from '../../shared/lib'
import { AccessDenied } from '../../shared/ui'
import { RealtimeProvider } from '../providers'

/**
 * Compone boards (carga + cabecera) con cards/lists (cuerpo): boards no conoce a las demas features.
 * El rol sobre el tablero es el del workspace padre (ADR-0009): se resuelve aqui y se pasa como `canEdit`.
 */
export function BoardPage() {
  const { boardId: raw } = useParams()
  if (!raw) return <p>Tablero no encontrado</p>
  return <BoardLoader id={boardId(raw)} />
}

function BoardLoader({ id }: { id: BoardId }) {
  const board = useBoard(id)
  if (board.isPending) return <p role="status">Cargando tablero...</p>
  if (board.isError) {
    if (isForbidden(board.error)) return <AccessDenied />
    return <p role="alert">No se pudo cargar el tablero: {board.error.message}</p>
  }
  // El tiempo real se monta con el tablero ya en cache (la carga REST es la fuente de verdad).
  return (
    <RealtimeProvider boardId={id}>
      <BoardWithRole id={id} workspaceId={board.data.board.workspaceId} />
    </RealtimeProvider>
  )
}

function BoardWithRole({ id, workspaceId }: { id: BoardId; workspaceId: WorkspaceId }) {
  const workspace = useWorkspace(workspaceId)
  if (workspace.isPending) return <p role="status">Cargando permisos...</p>
  if (workspace.isError) {
    if (isForbidden(workspace.error)) return <AccessDenied />
    return <p role="alert">No se pudieron cargar los permisos: {workspace.error.message}</p>
  }
  const editable = canEdit(workspace.data.role)
  return (
    <BoardScreen boardId={id} canEdit={editable}>
      {(board) => (
        <>
          <p>
            <Link to={`/workspaces/${board.board.workspaceId}`}>Volver al workspace</Link>
          </p>
          {!editable && <p className="notice">Solo lectura: tu rol en este workspace es Lector.</p>}
          <BoardView boardId={id} board={board} canEdit={editable} />
        </>
      )}
    </BoardScreen>
  )
}
