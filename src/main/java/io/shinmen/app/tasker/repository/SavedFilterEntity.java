package io.shinmen.app.tasker.repository;

import io.shinmen.app.tasker.model.BaseEntity;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saved_filters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedFilterEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String filterJson;
}
