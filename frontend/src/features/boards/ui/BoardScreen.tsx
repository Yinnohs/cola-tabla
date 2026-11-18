import { useState, type FormEvent, type ReactNode } from 'react'
import type { BoardId } from '../../../shared/kernel'
import { selectLists, type NormalizedBoard } from '../domain/normalized-board'
import { useBoard } from '../application/use-board'
import { useRenameBoard } from '../application/use-rename-board'

interface BoardScreenProps {
  boardId: BoardId
  /**
   * Cuerpo del tablero. `boards` no conoce `lists`/`cards`: quien compone (app/routes) inyecta aqui
   * la vista con listas y tarjetas. Sin `children` se muestra una vista de solo lectura.
   */
  children?: (board: NormalizedBoard) => ReactNode
}

/** Pantalla del tablero: carga + cabecera con renombrado; el cuerpo lo inyecta quien compone. */
export function BoardScreen({ boardId, children }: BoardScreenProps) {
  const { data, isPending, isError, error } = useBoard(boardId)
  const rename = useRenameBoard(boardId)
  const [draft, setDraft] = useState<string | null>(null)

  if (isPending) return <p role="status">Cargando tablero...</p>
  if (isError) return <p role="alert">No se pudo cargar el tablero: {error.message}</p>

  function submit(e: FormEvent) {
    e.preventDefault()
    if (draft === null) return
    rename.mutate(draft, { onSuccess: () => setDraft(null) })
  }

  return (
    <section aria-label="Tablero" className="board-screen">
      {draft === null ? (
        <h1 className="board-title">
          {data.board.name}{' '}
          <button type="button" className="btn-ghost" onClick={() => setDraft(data.board.name)}>
            Renombrar
          </button>
        </h1>
      ) : (
        <form onSubmit={submit} aria-label="Renombrar tablero" className="inline-form">
          <label>
            Nuevo nombre
            <input value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={120} />
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
      {children ? children(data) : <ReadOnlyLists board={data} />}
    </section>
  )
}

function ReadOnlyLists({ board }: { board: NormalizedBoard }) {
  return (
    <div>
      {selectLists(board).map((list) => (
        <section key={list.id} aria-label={list.name}>
          <h2>{list.name}</h2>
          <ul>
            {list.cards.map((card) => (
              <li key={card.id}>{card.title}</li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
