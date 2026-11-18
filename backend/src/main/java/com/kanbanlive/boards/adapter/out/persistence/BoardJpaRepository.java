package com.kanbanlive.boards.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BoardJpaRepository extends JpaRepository<BoardJpaEntity, UUID> {

    List<BoardJpaEntity> findByWorkspaceIdOrderByCreatedAtAscIdAsc(UUID workspaceId);
}
