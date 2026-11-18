import type { WorkspaceId } from '../../../shared/kernel'
import { useWorkspaces } from '../application/use-workspaces'

export function WorkspaceList({ onOpen }: { onOpen: (id: WorkspaceId) => void }) {
  const { data, isPending, isError, error } = useWorkspaces()
  if (isPending) return <p role="status">Cargando workspaces...</p>
  if (isError) return <p role="alert">No se pudieron cargar los workspaces: {error.message}</p>
  if (data.length === 0) return <p>Aún no tienes workspaces. Crea el primero.</p>
  return (
    <ul aria-label="Workspaces">
      {data.map((w) => (
        <li key={w.id}>
          <button type="button" onClick={() => onOpen(w.id)}>
            {w.name}
          </button>
        </li>
      ))}
    </ul>
  )
}
