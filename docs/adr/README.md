# Architecture Decision Records (ADR)

Decisiones de arquitectura de Kanban Live. Una decisión = un fichero `NNNN-titulo-en-kebab.md`.

## Reglas

- Numeración correlativa de 4 dígitos. Nunca se reutiliza ni se renumera.
- Un ADR aceptado **no se edita**; si cambia la decisión, se crea uno nuevo que lo reemplaza (`Superseded by ADR-XXXX`).
- Usar la plantilla [`0000-template.md`](0000-template.md).
- Los tests de arquitectura (ArchUnit / dependency-cruiser) nunca se desactivan: se corrige el diseño o se escribe un ADR.

## Índice

| ADR | Tema | Fase | Estado |
| --- | --- | --- | --- |
| [0001](0001-hexagonal-screaming-feature-slices.md) | Hexagonal + screaming (backend) y feature slices (frontend) | 0 | Aceptado |
| [0002](0002-dominio-separado-de-entidades-jpa.md) | Modelo de dominio separado de entidades JPA | 0 | Aceptado |
| 0003 | Fractional indexing (LexoRank-like) y rebalanceo | 1 | Pendiente |
| 0004 | Secuencia por tablero asignada en la transacción + log `board_event` | 3 | Pendiente |
| 0005 | Estrategia de conflictos (LWW para mover, 409 para editar) | 4 | Pendiente |
| 0006 | Renovación de JWT en WebSocket | 4 | Pendiente |
| 0007 | Redis pub/sub vs RabbitMQ broker relay | 6 | Pendiente |

Ver también: [`../../todos/`](../../todos/README.md) para las tareas que producen cada ADR.
