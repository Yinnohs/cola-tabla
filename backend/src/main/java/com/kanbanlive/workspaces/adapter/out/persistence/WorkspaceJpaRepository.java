package com.kanbanlive.workspaces.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkspaceJpaRepository extends JpaRepository<WorkspaceJpaEntity, UUID> {

    List<WorkspaceJpaEntity> findAllByOrderByCreatedAtAscIdAsc();
}
