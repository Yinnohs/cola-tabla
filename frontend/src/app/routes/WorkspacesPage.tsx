import { useNavigate } from 'react-router'
import { CreateWorkspaceForm, WorkspaceList } from '../../features/workspaces'

export function WorkspacesPage() {
  const navigate = useNavigate()
  return (
    <section>
      <h1>Workspaces</h1>
      <CreateWorkspaceForm onCreated={(w) => void navigate(`/workspaces/${w.id}`)} />
      <WorkspaceList onOpen={(id) => void navigate(`/workspaces/${id}`)} />
    </section>
  )
}
