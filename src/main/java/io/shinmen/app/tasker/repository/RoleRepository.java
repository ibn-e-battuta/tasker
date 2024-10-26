package io.shinmen.app.tasker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.shinmen.app.tasker.model.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
