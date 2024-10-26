package io.shinmen.app.tasker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.PasswordHistory;
import io.shinmen.app.tasker.model.User;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop3ByUserOrderByChangeDateDesc(User user);
}
