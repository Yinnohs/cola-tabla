import { Link, Route, Routes } from 'react-router'
import { BoardPage } from './BoardPage'
import { WorkspacePage } from './WorkspacePage'
import { WorkspacesPage } from './WorkspacesPage'

export function AppRoutes() {
  return (
    <>
      <header>
        <Link to="/">Kanban Live</Link>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<WorkspacesPage />} />
          <Route path="/workspaces/:workspaceId" element={<WorkspacePage />} />
          <Route path="/boards/:boardId" element={<BoardPage />} />
          <Route path="*" element={<p>Página no encontrada</p>} />
        </Routes>
      </main>
    </>
  )
}
