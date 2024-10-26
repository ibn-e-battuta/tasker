package io.shinmen.app.tasker.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.shinmen.app.tasker.model.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByOwnerId(Long ownerId);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE m.user.id = :userId")
    List<Team> findTeamsByMemberId(Long userId);

    boolean existsByNameAndOwnerId(String name, Long ownerId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Team t JOIN t.members m WHERE t.id = :teamId AND m.id = :userId")
    boolean existsByIdAndMembersId(@Param("teamId") Long teamId, @Param("userId") Long userId);
}
