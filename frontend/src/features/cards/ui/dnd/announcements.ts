import type { Announcements } from '@dnd-kit/core'
import type { CardId, ListId } from '../../../../shared/kernel'
import type { NormalizedBoard } from '../../../boards'
import { kindOf, resolveCardDragTarget, resolveListDragIndex } from './drop'

/** Instrucciones leidas por el lector de pantalla al enfocar un elemento arrastrable. */
export const SCREEN_READER_INSTRUCTIONS = {
  draggable:
    'Para recoger un elemento pulsa Espacio. Muévelo con las flechas. Pulsa Espacio de nuevo para soltarlo o Escape para cancelar.',
}

export const DND_SENSOR_KEY_CODES = {
  // Enter queda libre para abrir la tarjeta; Espacio recoge y suelta.
  start: ['Space'],
  cancel: ['Escape'],
  end: ['Space'],
}

function cardPlace(
  board: NormalizedBoard,
  active: Parameters<typeof resolveCardDragTarget>[1],
  over: Parameters<typeof resolveCardDragTarget>[2],
) {
  const drop = resolveCardDragTarget(board, active, over)
  if (!drop) return null
  const list = board.lists[drop.toListId]
  if (!list) return null
  const others = list.cardIds.filter((id) => id !== active.id).length
  return { list: list.name, position: drop.index + 1, total: others + 1 }
}

function listPlace(
  board: NormalizedBoard,
  active: Parameters<typeof resolveListDragIndex>[1],
  over: Parameters<typeof resolveListDragIndex>[2],
) {
  const index = resolveListDragIndex(board, active, over)
  return index === null ? null : { position: index + 1, total: board.listIds.length }
}

function names(board: NormalizedBoard, activeId: string) {
  return {
    card: board.cards[activeId as CardId]?.title ?? 'tarjeta',
    list: board.lists[activeId as ListId]?.name ?? 'lista',
  }
}

/** Mensajes en espanol para el lector de pantalla y para la region visible `aria-live`. */
export function buildAnnouncements(board: NormalizedBoard): Announcements {
  return {
    onDragStart({ active }) {
      const n = names(board, String(active.id))
      if (kindOf(active) === 'list') {
        const at = board.listIds.indexOf(active.id as ListId) + 1
        return `Lista "${n.list}" recogida, posición ${at} de ${board.listIds.length}. Usa las flechas izquierda y derecha para moverla, Espacio para soltarla.`
      }
      const card = board.cards[active.id as CardId]
      const list = card && board.lists[card.listId]
      const at = list ? list.cardIds.indexOf(card.id) + 1 : 0
      return `Tarjeta "${n.card}" recogida en la lista "${list?.name ?? ''}", posición ${at} de ${list?.cardIds.length ?? 0}. Usa las flechas para moverla, Espacio para soltarla, Escape para cancelar.`
    },
    onDragOver({ active, over }) {
      const n = names(board, String(active.id))
      if (kindOf(active) === 'list') {
        const p = listPlace(board, active, over)
        return p ? `Lista "${n.list}" en la posición ${p.position} de ${p.total}.` : undefined
      }
      const p = cardPlace(board, active, over)
      return p
        ? `Tarjeta "${n.card}" en la lista "${p.list}", posición ${p.position} de ${p.total}.`
        : undefined
    },
    onDragEnd({ active, over }) {
      const n = names(board, String(active.id))
      if (kindOf(active) === 'list') {
        const p = listPlace(board, active, over)
        return p
          ? `Lista "${n.list}" soltada en la posición ${p.position} de ${p.total}.`
          : `Lista "${n.list}" soltada sin cambios.`
      }
      const p = cardPlace(board, active, over)
      return p
        ? `Tarjeta "${n.card}" soltada en la lista "${p.list}", posición ${p.position} de ${p.total}.`
        : `Tarjeta "${n.card}" soltada sin cambios.`
    },
    onDragCancel({ active }) {
      const n = names(board, String(active.id))
      return kindOf(active) === 'list'
        ? `Movimiento cancelado. Lista "${n.list}" vuelve a su sitio.`
        : `Movimiento cancelado. Tarjeta "${n.card}" vuelve a su sitio.`
    },
  }
}
