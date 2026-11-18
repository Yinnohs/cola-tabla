interface CardViewProps {
  readonly title: string
  readonly description?: string
}

/** Contenido visual de una tarjeta (sin dnd ni logica): se reutiliza tambien en el overlay de arrastre. */
export function CardView({ title, description }: CardViewProps) {
  return (
    <div className="card-body">
      <span className="card-title">{title}</span>
      {description ? <span className="card-desc">{description}</span> : null}
    </div>
  )
}
