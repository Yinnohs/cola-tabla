package com.kanbanlive.lists.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ListJpaRepository extends JpaRepository<ListJpaEntity, UUID> {

    /** Mover = una fila, sin clausula de version (last-write-wins) y bumpeando la version. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update board_list set position = :position, updated_at = :updatedAt, "
            + "version = version + 1 where id = :id", nativeQuery = true)
    int move(@Param("id") UUID id, @Param("position") String position, @Param("updatedAt") Instant updatedAt);

    /** El orden lo hace Postgres sobre la columna position (COLLATE C). */
    @Query("select l.position from ListJpaEntity l where l.boardId = :boardId order by l.position desc limit 1")
    Optional<String> findLastPosition(@Param("boardId") UUID boardId);

    @Query("select l.position from ListJpaEntity l where l.boardId = :boardId and l.id = :id")
    Optional<String> findPositionInBoard(@Param("boardId") UUID boardId, @Param("id") UUID id);

    @Query("select l.id from ListJpaEntity l where l.boardId = :boardId order by l.position")
    List<UUID> findIdsInOrder(@Param("boardId") UUID boardId);
}
