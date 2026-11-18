import { useEffect, useId, useState, type FormEvent } from 'react'
import type { BoardId } from '../../../shared/kernel'
import type { Card } from '../domain/card'
import { CardConflictError } from '../application/errors'
import { useDeleteCard } from '../application/use-delete-card'
import { useUpdateCard } from '../application/use-update-card'

interface CardDialogProps {
  boardId: BoardId
  /** Tarjeta vigente en la cache; `undefined` si desaparecio (se cierra el dialogo). */
  card: Card | undefined
  onClose: () => void
}

/** Modal de edicion de tarjeta: titulo/descripcion, borrado y mensaje de conflicto (STALE_VERSION). */
export function CardDialog({ boardId, card, onClose }: CardDialogProps) {
  const titleId = useId()
  const update = useUpdateCard(boardId)
  const remove = useDeleteCard(boardId)
  const [title, setTitle] = useState(card?.title ?? '')
  const [description, setDescription] = useState(card?.description ?? '')
  // Version con la que se cargo el formulario: es la que se envia al guardar.
  const [loadedVersion, setLoadedVersion] = useState(card?.version ?? 0)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  // Elemento que tenia el foco al abrir (antes de que `autoFocus` lo mueva) para devolverselo al cerrar.
  const [opener] = useState<Element | null>(() => document.activeElement)

  useEffect(() => {
    return () => {
      if (opener instanceof HTMLElement) opener.focus()
    }
  }, [opener])

  useEffect(() => {
    if (!card) onClose()
  }, [card, onClose])

  if (!card) return null

  const conflict = update.error instanceof CardConflictError
  const otherError = update.isError && !conflict

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!card || conflict) return
    update.mutate(
      { cardId: card.id, title, description, expectedVersion: loadedVersion },
      { onSuccess: onClose },
    )
  }

  /** Descarta lo escrito y carga los datos vigentes (el tablero ya se refresco al detectar el conflicto). */
  function reload() {
    if (!card) return
    setTitle(card.title)
    setDescription(card.description)
    setLoadedVersion(card.version)
    update.reset()
  }

  function onDelete() {
    if (!card) return
    remove.mutate(card.id, { onSuccess: onClose })
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="dialog"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => e.key === 'Escape' && onClose()}
      >
        <h2 id={titleId}>Editar tarjeta</h2>
        <form onSubmit={submit}>
          <label>
            Título
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={255}
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
            <button type="submit" disabled={update.isPending || conflict}>
              Guardar
            </button>
            <button type="button" onClick={onClose}>
              Cancelar
            </button>
            {confirmingDelete ? (
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
