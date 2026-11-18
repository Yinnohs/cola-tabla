---
name: backend-hexagonal-screaming
description: Guía para trabajar en backend/ (Spring Boot, Java 21) de Kanban Live con arquitectura hexagonal y screaming architecture. Úsala al crear o modificar capacidades, casos de uso, puertos, adapters REST/WS/JPA/Redis, eventos de dominio o tests de arquitectura.
---

# Backend: Screaming + Hexagonal

Paquete raíz: `com.kanbanlive`. Lee primero `kanban-project-context`.

## Screaming: el primer nivel habla del negocio
```
workspaces/ membership/ boards/ lists/ cards/ collaboration/ presence/   ← capacidades
sharedkernel/                                                            ← bloques reutilizables, sin Spring
platform/{security,persistence,web,messaging}                            ← infraestructura transversal
```
PROHIBIDO en primer nivel: `controller`, `service`, `repository`, `config`, `util`, `model`.

## Estructura interna de TODA capacidad
```
<capacidad>/
├── domain/{model,event,error}                 # Java puro
├── application/{port/in, port/out, service}   # casos de uso
└── adapter/{in/{web,ws,event}, out/{persistence,redis}}
```
Nombres: `XxxUseCase` (port in), `XxxRepository`/`XxxPublisher`/`XxxStore` (port out), `XxxService` (implementa un use case), `XxxController`, `XxxJpaEntity`, `XxxPersistenceAdapter`, `XxxEntityMapper`, `XxxWebMapper`.

## Reglas de dependencia (ArchUnit las comprueba)
- `domain` → solo `sharedkernel.domain` y JDK. Nada de Spring, JPA, Jackson.
- `application` → su `domain`, `sharedkernel`, sus puertos. Nunca `adapter`. `@Transactional` permitido.
- `adapter.in` → `application.port.in`. `adapter.out` → `application.port.out` + `domain`. Nunca entre sí.
- Capacidad A → B: solo vía `B.application.port.in` o eventos de dominio de B. Jamás `B.domain.model` ni `B.adapter`.
- Nadie depende de `platform` excepto el arranque.

## Receta: añadir un caso de uso (ej. `MoveCard`)
1. **Domain**: método en el agregado (`Card.moveTo(listId, position)`) que valida invariantes y `registerEvent(new CardMoved(...))`. Test unitario sin Spring.
2. **Port in**: `MoveCardUseCase { Result execute(MoveCardCommand cmd); }` con `record MoveCardCommand(...)`.
3. **Port out**: lo mínimo necesario (`CardRepository.findById/save`). No crear puertos "por si acaso".
4. **Service**: `MoveCardService` (`@Transactional`), carga agregado → invoca dominio → `repo.save` → `DomainEventPublisher.publish(agg.pullEvents())`. Test con repositorio *fake* en memoria.
5. **Adapter out**: `CardPersistenceAdapter` implementa el puerto; mapea `CardJpaEntity ↔ Card` con `CardEntityMapper`. `@Version` solo en la entidad JPA; traducir `OptimisticLockingFailureException` → `StaleVersionException` (dominio).
6. **Adapter in**: `CardController` con DTOs propios (`record`), `@PreAuthorize("@perms.canEdit(#boardId)")`, mapper web → command. Nunca exponer entidades ni agregados.
7. **Flyway**: nueva migración `V{n}__descripcion.sql` (nunca editar una ya aplicada).
8. **Tests**: dominio + aplicación (fakes) + `@DataJpaTest` Testcontainers + `@WebMvcTest`. ArchUnit debe seguir en verde.

## Bloques reutilizables (úsalos, no los reinventes)
`AggregateRoot<ID>` (version + eventos pendientes), `DomainEvent`, `ValueObject` (records), `FractionalIndex` (`between(prev,next)`), `DomainEventPublisher`, `Clock`, `IdGenerator`, `DomainException` (+ handler `ProblemDetail` en `platform.web`), `PageQuery/Page` neutros. Si falta uno genérico, se añade a `sharedkernel` (con tests), no se copia en una capacidad.

## Tiempo real
- Eventos de dominio → `ApplicationEventPublisher` → `@TransactionalEventListener(AFTER_COMMIT)` en `collaboration/adapter/in/event`.
- `seq` por tablero se asigna **dentro** de la transacción (`UPDATE boards SET event_seq=event_seq+1 … RETURNING`) y se guarda en `board_event`.
- Salida por puerto `BoardEventBroadcaster`: `LocalStompBroadcaster` (Fase 3) / `RedisPubSubBroadcaster` (Fase 6). Cambiar de uno a otro no toca dominio ni aplicación.
- Sobre `CONNECT`: validar JWT. Sobre `SUBSCRIBE /topic/boards.{id}`: exigir `canView(boardId)`. Test negativo obligatorio.

## Seguridad
Roles `OWNER/EDITOR/VIEWER` en nuestra BD (tabla `membership`), no en Keycloak. `@perms` se apoya en un **puerto** de `membership`, no en JPA directo. Toda query de lectura filtra por membresía. Mantener la suite de aislamiento workspace A vs B para todos los endpoints nuevos.

## Antipatrones
Lógica en controllers · entidades JPA en el dominio · `@Service` con reglas de negocio que deberían estar en el agregado · eventos fuera de `AFTER_COMMIT` · reordenar listas completas · compartir entidades entre capacidades · puertos que devuelven tipos de Spring Data · desactivar ArchUnit.

## Antes de dar por terminado
`./gradlew test` (o `mvn verify`) en verde incluyendo `HexagonalArchitectureTest` y `ScreamingPackagesTest`; migración nueva si cambió el esquema; ADR si hubo decisión no trivial.
