package io.shinmen.app.tasker.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MentionNotification {
    private Long taskId;
    private Long commentId;
    private Long mentionedBy;
    private Long userId;
    private String mentionedByFullName;
    private String content;
    private LocalDateTime timestamp;
}
