package io.shinmen.app.tasker.repository.mongo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import io.shinmen.app.tasker.model.UserAudit;

public interface UserAuditRepository extends MongoRepository<UserAudit, String> {
    List<UserAudit> findByUserIdOrderByTimestampDesc(Long userId);

    @Query("{'userId': ?0, 'timestamp': {$gte: ?1, $lte: ?2}}")
    List<UserAudit> findByUserIdAndTimestampBetween(
        Long userId,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    @Query("{'action': ?0, 'timestamp': {$gte: ?1, $lte: ?2}}")
    List<UserAudit> findByActionAndTimestampBetween(
        String action,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
}
