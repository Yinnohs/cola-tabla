// API publica de la feature workspaces (unico punto de import desde fuera)
export type { Workspace } from './domain/workspace'
export type { WorkspaceRepository } from './application/ports/workspace-repository'
export { workspaceKeys } from './application/query-keys'
export { useWorkspace } from './application/use-workspace'
export { useWorkspaces } from './application/use-workspaces'
export { useCreateWorkspace, InvalidWorkspaceNameError } from './application/use-create-workspace'
export { WorkspaceList } from './ui/WorkspaceList'
export { CreateWorkspaceForm } from './ui/CreateWorkspaceForm'
