package io.shinmen.app.tasker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;

@Document(indexName = "tasks")
@Setting(settingPath = "elasticsearch/settings.json")
@Mapping(mappingPath = "elasticsearch/mappings.json")
@Data
@Builder
public class TaskDocument {
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
    private String status;

    @Field(type = FieldType.Keyword)
    private String priority;

    @Field(type = FieldType.Long)
    private Long assignedUserId;

    @Field(type = FieldType.Keyword)
    private Set<String> labels;

    @Field(type = FieldType.Date)
    private LocalDateTime dueDate;

    @Field(type = FieldType.Double)
    private Double estimatedEffort;

    @Field(type = FieldType.Text, analyzer = "custom_analyzer")
    private String commentText;

    @CompletionField(maxInputLength = 100)
    private List<String> suggest;
}
