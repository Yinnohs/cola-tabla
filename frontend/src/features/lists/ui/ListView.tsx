import { useState, type FormEvent, type ReactNode } from 'react'
import type { BoardId, ListId } from '../../../shared/kernel'
import { useRenameList } from '../application/use-rename-list'

interface ListViewProps {
  boardId: BoardId
  list: { readonly id: ListId; readonly name: string }
  /** Asa de arrastre de la lista (la inyecta quien integra dnd; `lists` no depende de dnd-kit). */
  handle?: ReactNode
  /** Tarjetas de la lista. */
  children?: ReactNode
  /** Zona inferior (p. ej. formulario de nueva tarjeta). */
  footer?: ReactNode
}

/** Columna de una lista: cabecera con renombrado inline + hueco para tarjetas y pie. */
export function ListView({ boardId, list, handle, children, footer }: ListViewProps) {
  const rename = useRenameList(boardId)
  const [draft, setDraft] = useState<string | null>(null)

  function submit(e: FormEvent) {
    e.preventDefault()
    if (draft === null) return
    rename.mutate({ listId: list.id, name: draft }, { onSuccess: () => setDraft(null) })
  }

  return (
    <section aria-label={`Lista ${list.name}`} className="list" data-list-id={list.id}>
      <header className="list-header">
        {handle}
        {draft === null ? (
          <>
            <h2 className="list-title">{list.name}</h2>
            <button
              type="button"
              className="btn-ghost"
              aria-label={`Renombrar lista ${list.name}`}
              onClick={() => setDraft(list.name)}
            >
              Renombrar
            </button>
          </>
        ) : (
          <form
            onSubmit={submit}
            aria-label={`Renombrar lista ${list.name}`}
            className="inline-form"
          >
            <label>
              Nuevo nombre de lista
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                maxLength={120}
                autoFocus
                onKeyDown={(e) => e.key === 'Escape' && setDraft(null)}
              />
            </label>
            <button type="submit" disabled={rename.isPending}>
              Guardar
            </button>
            <button type="button" onClick={() => setDraft(null)}>
              Cancelar
            </button>
            {rename.isError && <p role="alert">{rename.error.message}</p>}
          </form>
        )}
      </header>
      {children}
      {footer}
    </section>
  )
}
