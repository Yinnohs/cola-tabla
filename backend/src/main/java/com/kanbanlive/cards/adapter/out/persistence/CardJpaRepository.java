package com.kanbanlive.cards.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CardJpaRepository extends JpaRepository<CardJpaEntity, UUID> {

    /** Mover = una fila, sin clausula de version (last-write-wins) y bumpeando la version. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update card set list_id = :listId, position = :position, updated_at = :updatedAt, "
            + "version = version + 1 where id = :id", nativeQuery = true)
    int move(@Param("id") UUID id, @Param("listId") UUID listId, @Param("position") String position,
            @Param("updatedAt") Instant updatedAt);

    /** El orden lo hace Postgres sobre la columna position (COLLATE C). */
    @Query("select c.position from CardJpaEntity c where c.listId = :listId order by c.position desc limit 1")
    Optional<String> findLastPosition(@Param("listId") UUID listId);

    @Query("select c.position from CardJpaEntity c where c.listId = :listId and c.id = :id")
    Optional<String> findPositionInList(@Param("listId") UUID listId, @Param("id") UUID id);

    @Query("select c.id from CardJpaEntity c where c.listId = :listId order by c.position")
    List<UUID> findIdsInOrder(@Param("listId") UUID listId);
}
