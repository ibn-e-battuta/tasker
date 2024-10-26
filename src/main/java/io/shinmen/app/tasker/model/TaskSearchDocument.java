package io.shinmen.app.tasker.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.Set;

@Document(indexName = "tasks")
@Setting(settingPath = "elasticsearch/task-settings.json")
@Data
@Builder
public class TaskSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long taskId;

    @Field(type = FieldType.Long)
    private Long teamId;

    @Field(type = FieldType.Text, analyzer = "custom_analyzer")
    private String name;

    @Field(type = FieldType.Text, analyzer = "custom_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private Task.TaskStatus status;

    @Field(type = FieldType.Keyword)
    private Task.TaskPriority priority;

    @Field(type = FieldType.Long)
    private Long assignedUserId;

    @Field(type = FieldType.Keyword)
    private Set<String> labels;

    @Field(type = FieldType.Date)
    private LocalDateTime dueDate;

    @Field(type = FieldType.Double)
    private Double estimatedEffort;

    @Field(type = FieldType.Boolean)
    private boolean locked;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;

    @Field(type = FieldType.Text, analyzer = "custom_analyzer")
    private String comments;


    @CompletionField(maxInputLength = 100)
    private String suggest;
}
