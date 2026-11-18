package com.kanbanlive.lists.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ListJpaRepository extends JpaRepository<ListJpaEntity, UUID> {

    /** El orden lo hace Postgres sobre la columna position (COLLATE C). */
    @Query("select l.position from ListJpaEntity l where l.boardId = :boardId order by l.position desc limit 1")
    Optional<String> findLastPosition(@Param("boardId") UUID boardId);

    @Query("select l.position from ListJpaEntity l where l.boardId = :boardId and l.id = :id")
    Optional<String> findPositionInBoard(@Param("boardId") UUID boardId, @Param("id") UUID id);

    @Query("select l.id from ListJpaEntity l where l.boardId = :boardId order by l.position")
    List<UUID> findIdsInOrder(@Param("boardId") UUID boardId);
}
