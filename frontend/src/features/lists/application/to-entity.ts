import type { ListEntity } from '../../boards'
import type { List } from '../domain/list'

export function toListEntity(list: List): ListEntity {
  return { id: list.id, name: list.name, position: list.position, version: list.version }
}
