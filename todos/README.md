# Lista de tareas — Kanban Live

Derivada de `initial-plan.md` e `implentacion-inicial-colatabla.md`. Una carpeta por capa, un fichero por fase.

- [`back/`](back/) — backend (Spring Boot) + tareas transversales de repo/infra/CI/ADR
- [`front/`](front/) — frontend (React + Vite)
- ADRs: [`../docs/adr/`](../docs/adr/README.md) · Contratos: [`../docs/contracts/`](../docs/contracts/README.md)

## Reglas de trabajo

1. Leer antes `.claude/skills/kanban-project-context/SKILL.md` y la skill de la capa.
2. Una tarea = una rama/PR pequeña (≤ medio día). No mezclar capas ni fases.
3. Orden: dominio → casos de uso (con fakes) → adapters → UI.
4. Marcar `[x]` al terminar y actualizar la tabla de estado.
5. Las tareas con `🔗` dependen de la otra capa (ver "Depende de").

## Estado

| Fase | Back | Front |
| --- | --- | --- |
| 0 Base | [ ] [fase-0](back/fase-0-base.md) | [ ] [fase-0](front/fase-0-base.md) |
| 1 Dominio y REST | [ ] [fase-1](back/fase-1-dominio-rest.md) | [ ] [fase-1](front/fase-1-dominio-rest.md) |
| 2 Auth multi-tenant | [ ] [fase-2](back/fase-2-auth-multitenant.md) | [ ] [fase-2](front/fase-2-auth-multitenant.md) |
| 3 Tiempo real | [ ] [fase-3](back/fase-3-tiempo-real.md) | [ ] [fase-3](front/fase-3-tiempo-real.md) |
| 4 Conflictos/reconexión | [ ] [fase-4](back/fase-4-conflictos-reconexion.md) | [ ] [fase-4](front/fase-4-conflictos-reconexion.md) |
| 5 Presencia | [ ] [fase-5](back/fase-5-presencia.md) | [ ] [fase-5](front/fase-5-presencia.md) |
| 6 Escalado | [ ] [fase-6](back/fase-6-escalado.md) | [ ] [fase-6](front/fase-6-escalado.md) |
| 7 Rendimiento | [ ] [fase-7](back/fase-7-rendimiento.md) | [ ] [fase-7](front/fase-7-rendimiento.md) |

## Checklist de PR (trampas)

- [ ] ¿Se emite algún evento fuera de `AFTER_COMMIT`?
- [ ] ¿La suscripción WS valida permisos?
- [ ] ¿Algún movimiento reescribe más de una fila?
- [ ] ¿Algún cliente trata el WS como fuente de verdad?
- [ ] ¿Qué pasa si el JWT expira con el socket abierto?
- [ ] ¿`domain` importa algo de Spring/JPA/React?
- [ ] ¿Una feature importa archivos internos de otra?
- [ ] ¿Un DTO (REST/WS) se filtró hacia `domain`?
