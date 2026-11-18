package com.kanbanlive.cards.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CardJpaRepository extends JpaRepository<CardJpaEntity, UUID> {

    /** El orden lo hace Postgres sobre la columna position (COLLATE C). */
    @Query("select c.position from CardJpaEntity c where c.listId = :listId order by c.position desc limit 1")
    Optional<String> findLastPosition(@Param("listId") UUID listId);

    @Query("select c.position from CardJpaEntity c where c.listId = :listId and c.id = :id")
    Optional<String> findPositionInList(@Param("listId") UUID listId, @Param("id") UUID id);

    @Query("select c.id from CardJpaEntity c where c.listId = :listId order by c.position")
    List<UUID> findIdsInOrder(@Param("listId") UUID listId);
}
