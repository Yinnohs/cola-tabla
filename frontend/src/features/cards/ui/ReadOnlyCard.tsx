import type { BoardCardView } from '../../boards'
import { CardView } from './CardView'

interface ReadOnlyCardProps {
  card: BoardCardView
  onOpen: (card: BoardCardView) => void
}

/** Tarjeta de solo lectura (rol VIEWER): se puede abrir para leerla, pero no arrastrar ni editar. */
export function ReadOnlyCard({ card, onOpen }: ReadOnlyCardProps) {
  return (
    <li className="card" data-card-id={card.id}>
      <button type="button" className="card-handle" onClick={() => onOpen(card)}>
        <CardView title={card.title} description={card.description} />
      </button>
    </li>
  )
}
