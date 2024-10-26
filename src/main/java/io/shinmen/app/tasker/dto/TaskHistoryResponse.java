package io.shinmen.app.tasker.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class TaskHistoryResponse {
    private String id;
    private String action;
    private String field;
    private Object oldValue;
    private Object newValue;
    private UserResponse user;
    private LocalDateTime timestamp;
    private Map<String, Object> additionalInfo;
}
