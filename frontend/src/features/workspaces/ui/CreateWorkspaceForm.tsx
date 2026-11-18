import { useState, type FormEvent } from 'react'
import type { Workspace } from '../domain/workspace'
import { useCreateWorkspace } from '../application/use-create-workspace'

export function CreateWorkspaceForm({ onCreated }: { onCreated?: (w: Workspace) => void }) {
  const [name, setName] = useState('')
  const create = useCreateWorkspace()

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate(name, {
      onSuccess: (w) => {
        setName('')
        onCreated?.(w)
      },
    })
  }

  return (
    <form onSubmit={submit} aria-label="Crear workspace">
      <label>
        Nombre del workspace
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} />
      </label>
      <button type="submit" disabled={create.isPending}>
        Crear workspace
      </button>
      {create.isError && <p role="alert">{create.error.message}</p>}
    </form>
  )
}
