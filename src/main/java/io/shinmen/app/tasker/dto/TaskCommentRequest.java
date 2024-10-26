package io.shinmen.app.tasker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaskCommentRequest {
    @NotBlank(message = "Comment content is required")
    @Size(max = 5000, message = "Comment cannot exceed 5000 characters")
    private String content;
}
