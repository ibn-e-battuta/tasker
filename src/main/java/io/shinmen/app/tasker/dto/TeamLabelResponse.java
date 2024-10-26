package io.shinmen.app.tasker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TeamLabelResponse {
    private Long id;
    private String name;
    private String description;
    private String colorCode;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}