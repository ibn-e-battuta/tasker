package io.shinmen.app.tasker.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavedFilter {
    private Long id;
    private String name;
    private TaskFilter filter;
    private Long userId;
    private Long teamId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
