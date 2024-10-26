package io.shinmen.app.tasker.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedFilterRepository extends JpaRepository<SavedFilterEntity, Long> {
    List<SavedFilterEntity> findByUserIdAndTeamId(Long userId, Long teamId);
    boolean existsByUserIdAndTeamIdAndName(Long userId, Long teamId, String name);
}
