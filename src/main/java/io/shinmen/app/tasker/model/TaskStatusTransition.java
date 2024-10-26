package io.shinmen.app.tasker.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "task_status_transitions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "from_status", "to_status"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusTransition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "from_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Task.TaskStatus fromStatus;

    @Column(name = "to_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Task.TaskStatus toStatus;

    @Column(nullable = false)
    private boolean allowed;
}
