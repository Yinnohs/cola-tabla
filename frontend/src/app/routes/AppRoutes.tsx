import { Link, Route, Routes } from 'react-router'
import { AuthCallback, ProtectedRoute, SignedOut, UserMenu } from '../../features/auth'
import { ForbiddenBanner } from '../providers'
import { BoardPage } from './BoardPage'
import { WorkspacePage } from './WorkspacePage'
import { WorkspacesPage } from './WorkspacesPage'

export function AppRoutes() {
  return (
    <>
      <header>
        <Link to="/">Kanban Live</Link>
        <UserMenu />
      </header>
      <ForbiddenBanner />
      <main>
        <Routes>
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route path="/auth/signed-out" element={<SignedOut />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<WorkspacesPage />} />
            <Route path="/workspaces/:workspaceId" element={<WorkspacePage />} />
            <Route path="/boards/:boardId" element={<BoardPage />} />
          </Route>
          <Route path="*" element={<p>Página no encontrada</p>} />
        </Routes>
      </main>
    </>
  )
}
