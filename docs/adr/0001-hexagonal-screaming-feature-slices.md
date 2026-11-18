# ADR-0001: Hexagonal + screaming (backend) y feature slices (frontend)

- **Estado:** Aceptado
- **Fecha:** 2026-11-18
- **Ámbito:** transversal

## Contexto

Kanban Live combina reglas de negocio con matices (permisos por workspace, orden por fractional indexing, conflictos de versión) e infraestructura variable (REST, STOMP, Redis, Keycloak). Varios agentes y personas trabajarán en paralelo, y el plan prevé cambios de infraestructura sin tocar el negocio (por ejemplo, pasar de broadcast STOMP local a Redis pub/sub en la Fase 6). Sin límites explícitos, la organización por capas técnicas (`controller`, `service`, `repository`) acopla todo con todo y oculta qué hace el sistema. En el frontend ocurre lo mismo con carpetas `components/hooks/services`.

## Decisión

**Backend.** Screaming architecture: el primer nivel de paquetes bajo `com.kanbanlive` nombra capacidades de negocio (`workspaces`, `membership`, `boards`, `lists`, `cards`, `collaboration`, `presence`), más `sharedkernel` (bloques sin Spring) y `platform` (cableado transversal). Dentro de cada capacidad, hexagonal: `domain` <- `application` (`port.in`, `port.out`, `service`) <- `adapter` (`in.web`, `in.ws`, `out.persistence`, `out.redis`). Una capacidad solo usa a otra mediante `application.port.in` o eventos de dominio; nunca su `domain.model` ni sus adapters.

**Frontend.** El core se divide por feature (`auth`, `workspaces`, `boards`, `lists`, `cards`, `realtime`, `presence`), cada una con capas `domain/ application/ infrastructure/ ui/` y una API pública `index.ts`. Lo compartido va en `shared/` y el cableado (DI con `PortsContext`, providers, rutas) en `app/`, único lugar que conoce adapters concretos.

**Verificación automática.** Las reglas se hacen cumplir en CI: ArchUnit (`HexagonalArchitectureTest`, `ScreamingPackagesTest`) y dependency-cruiser. Los tests de arquitectura nunca se desactivan: se corrige el diseño o se escribe un ADR nuevo.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| Capas técnicas clásicas (controller/service/repository; components/hooks/services) | Familiar, menos ceremonia | Acoplamiento difuso, no comunica el negocio, difícil aislar cambios de infraestructura |
| Hexagonal global (un solo dominio/application/adapter para todo el sistema) | Aislamiento de infraestructura | Dominio monolítico, capacidades sin fronteras claras |
| Modulith (Spring Modulith) sin hexagonal interna | Verificación de módulos integrada | No fuerza separación dominio/adapter dentro del módulo; el frontend queda sin equivalente |
| Microservicios por capacidad | Aislamiento total | Coste operativo desproporcionado para el alcance |
| **Elegida:** screaming + hexagonal por capacidad / feature slices con capas | Fronteras claras, adapters intercambiables, testeable con fakes, verificable | Más ficheros y mapeos, curva inicial |

## Consecuencias

- Positivas: el árbol de paquetes comunica el dominio; cambiar Local STOMP por Redis es un adapter nuevo; casos de uso testeables con puertos fake sin Spring ni red; revisiones de PR acotadas por capacidad.
- Negativas / deuda aceptada: más boilerplate (puertos, DTOs, mappers); duplicación controlada entre backend y frontend (por ejemplo, fractional index con vectores de prueba comunes en `docs/contracts/`); disciplina necesaria para no saltarse `port.in`.
- Cómo se verifica: ArchUnit en el build del backend; dependency-cruiser en el job de frontend del CI; ambos obligatorios para fusionar a `main`.

## Referencias

- `initial-plan.md` secciones 4 (backend), 5 (frontend) y 10
- `todos/back/fase-0-base.md` (B0.5, B0.10), `todos/front/fase-0-base.md` (F0.4)
