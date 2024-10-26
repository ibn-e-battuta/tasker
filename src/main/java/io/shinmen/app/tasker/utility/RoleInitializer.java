package io.shinmen.app.tasker.utility;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.shinmen.app.tasker.model.Role;
import io.shinmen.app.tasker.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoleInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        if (roleRepository.count() == 0) {
            List<Role> defaultRoles = Arrays.asList(
                Role.builder()
                    .name("ROLE_ADMIN")
                    .description("System administrator with full access")
                    .build(),
                Role.builder()
                    .name("ROLE_TEAM_OWNER")
                    .description("Can create and manage teams")
                    .build(),
                Role.builder()
                    .name("ROLE_USER")
                    .description("Regular user with basic access")
                    .build()
            );

            roleRepository.saveAll(defaultRoles);
            log.info("Default roles initialized successfully");
        }
    }
}
