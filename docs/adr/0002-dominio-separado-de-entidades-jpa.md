# ADR-0002: Modelo de dominio separado de entidades JPA

- **Estado:** Aceptado
- **Fecha:** 2026-11-18
- **Ámbito:** backend

## Contexto

Las reglas de dependencia (ADR-0001) prohíben que `domain` dependa de Spring, JPA o Jackson. Además, los agregados registran eventos de dominio y deben poder probarse sin contenedor. Las entidades JPA, en cambio, necesitan constructor sin argumentos, anotaciones, proxies perezosos y `@Version` gestionado por el proveedor. Mezclar ambos obliga a que el dominio conozca el ORM y a que cambios de esquema repercutan en el negocio. Hay además un requisito de concurrencia: editar debe devolver `409` ante versión obsoleta.

## Decisión

Cada capacidad tiene dos modelos distintos:

- **Dominio** (`<cap>.domain.model`): clases/records Java puros, invariantes en constructores y métodos, `long version` como dato del agregado, eventos registrados con `registerEvent`.
- **Persistencia** (`<cap>.adapter.out.persistence`): `XxxJpaEntity` con `@Version`, repositorio Spring Data `XxxJpaRepository` y un `XxxEntityMapper` que convierte en ambos sentidos. Un `XxxPersistenceAdapter` implementa el puerto `application.port.out.XxxRepository`.

El `@Version` vive en la entidad JPA y se refleja en el agregado como `long version`. El adapter traduce `OptimisticLockingFailureException` a `StaleVersionException` (dominio), que `platform.web` convierte en `409 ProblemDetail`. Los DTOs REST son un tercer modelo (`adapter.in.web`), con su propio mapper. La entidad JPA nunca sale del adapter de persistencia.

## Alternativas consideradas

| Alternativa | Pros | Contras |
| --- | --- | --- |
| Entidad JPA como modelo de dominio (anotada) | Menos código, sin mappers | Dominio acoplado a JPA, constructores/proxies que rompen invariantes, tests con Spring, viola ArchUnit |
| Dominio puro con mapping XML/orm.xml | Dominio sin anotaciones | Configuración opaca, poco soporte de herramientas |
| Exponer entidades JPA directamente en REST | Mínimo código | Fugas de esquema al contrato, problemas de lazy loading y serialización |
| **Elegida:** modelos separados con mapper en el adapter | Dominio puro y testeable, esquema evolucionable, concurrencia explícita | Mappers extra, riesgo de desajuste entre modelos |

## Consecuencias

- Positivas: tests de dominio y de aplicación sin Spring; el esquema (Flyway) puede cambiar sin tocar el dominio; el manejo de versión optimista queda en un único punto.
- Negativas / deuda aceptada: boilerplate de mappers y posible desincronización (se mitiga con tests de round-trip de mapper y `@DataJpaTest` con Testcontainers); coste de mapeo por operación, aceptable en este alcance.
- Cómo se verifica: ArchUnit (`domain` sin `jakarta.persistence`, `org.springframework`, Jackson; `adapter.in` no depende de `adapter.out`), tests de mapper y de integración con Postgres real.

## Referencias

- `initial-plan.md` secciones 4.2, 4.3 y 4.6
- ADR-0001
