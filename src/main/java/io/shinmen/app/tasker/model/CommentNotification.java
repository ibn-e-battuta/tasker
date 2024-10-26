package io.shinmen.app.tasker.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentNotification {
    private Long taskId;
    private Long commentId;
    private String action;
    private Long userId;
    private String userFullName;
    private String content;
    private LocalDateTime timestamp;
}
