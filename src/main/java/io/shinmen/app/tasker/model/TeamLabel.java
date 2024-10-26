package io.shinmen.app.tasker.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_labels",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "name"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamLabel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "color_code", length = 7)
    private String colorCode;

    @Column(name = "is_default")
    private boolean isDefault;
}
