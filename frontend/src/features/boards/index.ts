// API publica de la feature boards (unico punto de import desde fuera)
export type { Board } from './domain/board'
export type { BoardCardView, BoardDetails, BoardListView } from './domain/board-details'
export {
  normalizeBoard,
  removeCard,
  selectCardsOf,
  selectLists,
  upsertCard,
  upsertList,
  withBoard,
  type ListEntity,
  type NormalizedBoard,
  type NormalizedList,
} from './domain/normalized-board'
export type { BoardRepository } from './application/ports/board-repository'
export { boardKeys } from './application/query-keys'
export { useBoards } from './application/use-boards'
export { useBoard } from './application/use-board'
export { useCreateBoard, InvalidBoardNameError } from './application/use-create-board'
export { useRenameBoard } from './application/use-rename-board'
export { BoardScreen } from './ui/BoardScreen'
export { CreateBoardForm } from './ui/CreateBoardForm'
