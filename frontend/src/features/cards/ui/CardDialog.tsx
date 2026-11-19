import { useCallback, useId, useState, type FormEvent } from 'react'
import type { BoardId } from '../../../shared/kernel'
import type { Card } from '../domain/card'
import { CardConflictError } from '../application/errors'
import { useDeleteCard } from '../application/use-delete-card'
import { useUpdateCard } from '../application/use-update-card'

interface CardDialogProps {
  boardId: BoardId
  /** Tarjeta vigente en la cache (el padre solo monta el dialogo mientras exista). */
  card: Card
  onClose: () => void
  /** true (rol VIEWER): solo lectura, sin guardar ni eliminar. */
  readOnly?: boolean
}

/** Modal de edicion de tarjeta: titulo/descripcion, borrado y mensaje de conflicto (STALE_VERSION). */
export function CardDialog({ boardId, card, onClose, readOnly = false }: CardDialogProps) {
  const titleId = useId()
  const update = useUpdateCard(boardId)
  const remove = useDeleteCard(boardId)
  const [title, setTitle] = useState(card.title)
  const [description, setDescription] = useState(card.description)
  // Version con la que se cargo el formulario: es la que se envia al guardar.
  const [loadedVersion, setLoadedVersion] = useState(card.version)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // Elemento que tenia el foco al abrir (antes de que `autoFocus` lo mueva) para devolverselo al cerrar.
  const [opener] = useState<Element | null>(() => document.activeElement)

  // Ref callback con cleanup (React 19): al desmontar el dialogo devuelve el foco. Debe ser estable.
  const restoreFocusOnUnmount = useCallback(
    () => () => {
      if (opener instanceof HTMLElement) opener.focus()
    },
    [opener],
  )

  const conflict = update.error instanceof CardConflictError
  const otherError = update.isError && !conflict

  function submit(e: FormEvent) {
    e.preventDefault()
    if (conflict) return
    update.mutate(
      { cardId: card.id, title, description, expectedVersion: loadedVersion },
      { onSuccess: onClose },
    )
  }

  /** Descarta lo escrito y carga los datos vigentes (el tablero ya se refresco al detectar el conflicto). */
  function reload() {
    setTitle(card.title)
    setDescription(card.description)
    setLoadedVersion(card.version)
    update.reset()
  }

  function onDelete() {
    remove.mutate(card.id, { onSuccess: onClose })
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div
        ref={restoreFocusOnUnmount}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="dialog"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => e.key === 'Escape' && onClose()}
      >
        <h2 id={titleId}>{readOnly ? 'Tarjeta' : 'Editar tarjeta'}</h2>
        <form onSubmit={submit}>
          <label>
            Título
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={255}
              readOnly={readOnly}
              autoFocus
            />
          </label>
          <label>
            Descripción
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={10000}
              rows={6}
              readOnly={readOnly}
            />
          </label>
          {conflict && (
            <div role="alert" className="conflict">
              <p>
                Esta tarjeta ha sido modificada por otra persona mientras la editabas. Tus cambios
                no se han guardado.
              </p>
              <button type="button" onClick={reload}>
                Recargar datos actuales
              </button>
            </div>
          )}
          {otherError && <p role="alert">{update.error.message}</p>}
          {remove.isError && <p role="alert">{remove.error.message}</p>}
          <div className="dialog-actions">
            {!readOnly && (
              <button type="submit" disabled={update.isPending || conflict}>
                Guardar
              </button>
            )}
            <button type="button" onClick={onClose}>
              {readOnly ? 'Cerrar' : 'Cancelar'}
            </button>
            {readOnly ? null : confirmingDelete ? (
              <>
                <button type="button" className="btn-danger" onClick={onDelete}>
                  Confirmar eliminación
                </button>
                <button type="button" onClick={() => setConfirmingDelete(false)}>
                  No eliminar
                </button>
              </>
            ) : (
              <button
                type="button"
                className="btn-danger"
                onClick={() => setConfirmingDelete(true)}
              >
                Eliminar tarjeta
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}
