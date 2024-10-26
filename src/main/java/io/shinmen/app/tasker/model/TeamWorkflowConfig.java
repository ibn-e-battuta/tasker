package io.shinmen.app.tasker.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "team_workflow_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamWorkflowConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ElementCollection
    @CollectionTable(name = "team_final_statuses",
            joinColumns = @JoinColumn(name = "workflow_config_id"))
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Set<Task.TaskStatus> finalStatuses = new HashSet<>();

    @Column(name = "allow_status_reversion")
    private boolean allowStatusReversion;

    @ElementCollection
    @CollectionTable(name = "team_status_reversion_roles",
            joinColumns = @JoinColumn(name = "workflow_config_id"))
    @Column(name = "role_id")
    private Set<Long> statusReversionRoles = new HashSet<>();
}
