package io.shinmen.app.tasker.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "task_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskHistory {

    @Id
    private String id;

    @Field("task_id")
    private Long taskId;

    @Field("user_id")
    private Long userId;

    @Field("action")
    private String action;

    @Field("field")
    private String field;

    @Field("old_value")
    private Object oldValue;

    @Field("new_value")
    private Object newValue;

    @Field("additional_info")
    private Map<String, Object> additionalInfo;

    @Field("timestamp")
    private LocalDateTime timestamp;
}
