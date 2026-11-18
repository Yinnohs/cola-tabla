import { useState, type FormEvent } from 'react'
import type { WorkspaceId } from '../../../shared/kernel'
import type { Board } from '../domain/board'
import { useCreateBoard } from '../application/use-create-board'

export function CreateBoardForm({
  workspaceId,
  onCreated,
}: {
  workspaceId: WorkspaceId
  onCreated?: (b: Board) => void
}) {
  const [name, setName] = useState('')
  const create = useCreateBoard(workspaceId)

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate(name, {
      onSuccess: (b) => {
        setName('')
        onCreated?.(b)
      },
    })
  }

  return (
    <form onSubmit={submit} aria-label="Crear tablero">
      <label>
        Nombre del tablero
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} />
      </label>
      <button type="submit" disabled={create.isPending}>
        Crear tablero
      </button>
      {create.isError && <p role="alert">{create.error.message}</p>}
    </form>
  )
}
