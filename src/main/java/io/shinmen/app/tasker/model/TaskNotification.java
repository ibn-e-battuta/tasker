package io.shinmen.app.tasker.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

import java.time.LocalDateTime;

@Data
@Builder
public class TaskNotification {
    private Long taskId;
    private String action;
    private Long userId;
    private String userFullName;
    private Object oldValue;
    private Object newValue;
    private Map<String, Object> data;
    private LocalDateTime timestamp;
}
