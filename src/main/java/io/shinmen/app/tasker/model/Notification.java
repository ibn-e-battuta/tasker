package io.shinmen.app.tasker.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class Notification {
    private Long id;
    private String type;
    private String title;
    private String message;
    private Object data;
    private boolean read;
    private LocalDateTime createdAt;
}
