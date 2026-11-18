import { useState, type FormEvent } from 'react'
import type { BoardId } from '../../../shared/kernel'
import { useCreateList } from '../application/use-create-list'

export function CreateListForm({ boardId }: { boardId: BoardId }) {
  const [name, setName] = useState('')
  const create = useCreateList(boardId)

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate(name, { onSuccess: () => setName('') })
  }

  return (
    <form onSubmit={submit} className="inline-form create-list-form" aria-label="Crear lista">
      <label>
        Nombre de la lista
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={120} />
      </label>
      <button type="submit" disabled={create.isPending}>
        Añadir lista
      </button>
      {create.isError && <p role="alert">{create.error.message}</p>}
    </form>
  )
}
