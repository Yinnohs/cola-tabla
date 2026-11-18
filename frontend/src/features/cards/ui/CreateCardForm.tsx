import { useState, type FormEvent } from 'react'
import type { BoardId, ListId } from '../../../shared/kernel'
import { useCreateCard } from '../application/use-create-card'

interface CreateCardFormProps {
  boardId: BoardId
  listId: ListId
  listName: string
}

/** Alta inline de tarjeta: boton que despliega un formulario con titulo. */
export function CreateCardForm({ boardId, listId, listName }: CreateCardFormProps) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const create = useCreateCard(boardId)

  function close() {
    setOpen(false)
    setTitle('')
    create.reset()
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate({ listId, title }, { onSuccess: () => setTitle('') })
  }

  if (!open) {
    return (
      <button type="button" className="btn-ghost add-card" onClick={() => setOpen(true)}>
        Añadir tarjeta<span className="sr-only"> en {listName}</span>
      </button>
    )
  }
  return (
    <form
      onSubmit={submit}
      aria-label={`Nueva tarjeta en ${listName}`}
      className="inline-form"
      onKeyDown={(e) => e.key === 'Escape' && close()}
    >
      <label>
        Título de la tarjeta
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={255} autoFocus />
      </label>
      <button type="submit" disabled={create.isPending}>
        Añadir
      </button>
      <button type="button" onClick={close}>
        Cerrar
      </button>
      {create.isError && <p role="alert">{create.error.message}</p>}
    </form>
  )
}
