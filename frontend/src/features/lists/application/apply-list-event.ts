import { reconcileList, type ListEntity, type NormalizedBoard } from '../../boards'

/** Eventos de lista ya traducidos al dominio. No existe `list.deleted` en el contrato (version 1). */
export interface ListEvent {
  readonly type: 'list.created' | 'list.updated' | 'list.moved'
  readonly list: ListEntity
}

/**
 * Reducer puro `(NormalizedBoard, ListEvent) -> NormalizedBoard`: upsert guardado por `version`; conserva las
 * tarjetas de la lista y reordena `listIds` por posicion. Vive en `application` por la misma razon que
 * `applyCardEvent` (usa helpers de cache de `boards`).
 */
export function applyListEvent(nb: NormalizedBoard, event: ListEvent): NormalizedBoard {
  return reconcileList(nb, event.list)
}
