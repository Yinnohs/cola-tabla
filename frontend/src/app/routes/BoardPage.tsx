import { Link, useParams } from 'react-router'
import { BoardScreen } from '../../features/boards'
import { BoardView } from '../../features/cards'
import { boardId } from '../../shared/kernel'

/** Compone boards (carga + cabecera) con cards/lists (cuerpo): boards no conoce a las demas features. */
export function BoardPage() {
  const { boardId: raw } = useParams()
  if (!raw) return <p>Tablero no encontrado</p>
  const id = boardId(raw)
  return (
    <BoardScreen boardId={id}>
      {(board) => (
        <>
          <p>
            <Link to={`/workspaces/${board.board.workspaceId}`}>Volver al workspace</Link>
          </p>
          <BoardView boardId={id} board={board} />
        </>
      )}
    </BoardScreen>
  )
}
