# Back — Fase 6: Escalado horizontal

**DoD:** clientes en instancias distintas se ven entre sí; apagar una instancia solo provoca reconexión transparente.

- [ ] **B6.1** `infra/`: Nginx con upgrade WebSocket (`Upgrade`/`Connection`, timeouts) + 2 réplicas del backend en Compose
- [ ] **B6.2** `RedisPubSubBroadcaster` en `platform/messaging`/`collaboration/adapter/out/redis`, implementa el mismo puerto `BoardEventBroadcaster` (cero cambios en dominio/aplicación)
  - [ ] Publicar en canal Redis; cada instancia reenvía a sus suscriptores STOMP locales
- [ ] **B6.3** Evitar doble entrega (la instancia origen no reenvía dos veces); `instanceId` o publicar solo vía Redis
- [ ] **B6.4** Presencia compatible con multi-instancia (estado en Redis, no en memoria)
- [ ] **B6.5** ADR-0007: Redis pub/sub vs RabbitMQ broker relay
- [ ] **B6.6** Test: clientes en instancias distintas se ven; matar una instancia ⇒ reconexión transparente (🔗 F6.1)
