package io.shinmen.app.tasker.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.shinmen.app.tasker.dto.TaskSearchRequest;
import io.shinmen.app.tasker.dto.TaskSearchResponse;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.TaskComment;
import io.shinmen.app.tasker.model.TaskDocument;
import io.shinmen.app.tasker.repository.TaskCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSearchService {

    private static final String INDEX_NAME = "tasks";
    private static final String FIELD_TEAM_ID = "teamId";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_COMMENT_TEXT = "commentText";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PRIORITY = "priority";
    private static final String FIELD_LABELS = "labels";

    private static final String TAG_PRE = "<em>";
    private static final String TAG_POST = "</em>";

    private final ElasticsearchClient elasticsearchClient;
    private final TaskCommentRepository taskCommentRepository;
    private final EnhancedCacheService cacheService;

    public Page<TaskSearchResponse> searchTasks(Long teamId, Long userId,
            TaskSearchRequest request, Pageable pageable) {
        String cacheKey = generateSearchCacheKey(teamId, request, pageable);

        return cacheService.getOrCompute(cacheKey, Duration.ofMinutes(5), () -> {
            try {
                SearchResponse<TaskDocument> response = elasticsearchClient.search(
                        buildSearchRequest(teamId, request, pageable),
                        TaskDocument.class);

                return new PageImpl<>(
                        response.hits().hits().stream()
                                .map(this::mapToSearchResponse)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()),
                        pageable,
                        response.hits().total().value());
            } catch (IOException e) {
                log.error("Error executing elasticsearch search: {}", e.getMessage(), e);
                throw new CustomException("Search operation failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        });
    }

    private SearchRequest buildSearchRequest(Long teamId, TaskSearchRequest request,
            Pageable pageable) {
        return SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(buildQueryWithFilters(teamId, request))
                .from(pageable.getPageNumber() * pageable.getPageSize())
                .size(pageable.getPageSize())
                .highlight(h -> h
                        .fields(FIELD_NAME, f -> f
                                .numberOfFragments(0)
                                .preTags(TAG_PRE)
                                .postTags(TAG_POST))
                        .fields(FIELD_DESCRIPTION, f -> f
                                .numberOfFragments(3)
                                .fragmentSize(150)
                                .preTags(TAG_PRE)
                                .postTags(TAG_POST))
                        .fields(FIELD_COMMENT_TEXT, f -> f
                                .numberOfFragments(3)
                                .fragmentSize(150)
                                .preTags(TAG_PRE)
                                .postTags(TAG_POST))));
    }

    private Query buildQueryWithFilters(Long teamId, TaskSearchRequest request) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        boolQuery.must(Query.of(q -> q
                .term(t -> t
                        .field(FIELD_TEAM_ID)
                        .value(teamId))));

        if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
            boolQuery.must(Query.of(q -> q
                    .multiMatch(m -> m
                            .fields(FIELD_NAME + "^3", FIELD_DESCRIPTION + "^2", FIELD_COMMENT_TEXT)
                            .query(request.getSearchTerm())
                            .operator(Operator.And)
                            .fuzziness("AUTO"))));
        }

        addStatusFilter(boolQuery, request.getStatus());
        addPriorityFilter(boolQuery, request.getPriority());
        addLabelsFilter(boolQuery, request.getLabels());

        return Query.of(q -> q.bool(boolQuery.build()));
    }

    private void addStatusFilter(BoolQuery.Builder boolQuery, Task.TaskStatus status) {
        if (status != null) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field(FIELD_STATUS)
                            .value(v -> v.stringValue(status.name())))));
        }
    }

    private void addPriorityFilter(BoolQuery.Builder boolQuery, Task.TaskPriority priority) {
        if (priority != null) {
            boolQuery.filter(Query.of(q -> q
                    .term(t -> t
                            .field(FIELD_PRIORITY)
                            .value(v -> v.stringValue(priority.name())))));
        }
    }

    private void addLabelsFilter(BoolQuery.Builder boolQuery, Set<String> labels) {
        if (labels != null && !labels.isEmpty()) {
            boolQuery.filter(Query.of(q -> q
                    .terms(t -> t
                            .field(FIELD_LABELS)
                            .terms(tt -> tt
                                    .value(labels.stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList()))))));
        }
    }

    public List<String> getSuggestions(Long teamId, String prefix) {
        String cacheKey = String.format("suggestions:%d:%s", teamId, prefix);

        return cacheService.getOrCompute(cacheKey, Duration.ofMinutes(5), () -> {
            try {
                SearchResponse<TaskDocument> response = elasticsearchClient.search(
                        buildSuggestionRequest(teamId, prefix),
                        TaskDocument.class);

                return extractSuggestions(response);
            } catch (IOException e) {
                log.error("Error getting suggestions: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        });
    }

    private SearchRequest buildSuggestionRequest(Long teamId, String prefix) {
        return SearchRequest.of(s -> s
            .index(INDEX_NAME)
            .source(src -> src
                .filter(f -> f
                    .includes(FIELD_NAME)
                )
            )
            .size(0)
        );
    }

    public List<String> getSuggestionsForField(Long teamId, String field, String prefix) {
        String cacheKey = String.format("field-suggestions:%d:%s:%s", teamId, field, prefix);

        return cacheService.getOrCompute(cacheKey, Duration.ofMinutes(5), () -> {
            try {
                SearchResponse<TaskDocument> response = elasticsearchClient.search(
                        buildFieldSuggestionRequest(teamId, field, prefix),
                        TaskDocument.class);

                return extractFieldSuggestions(response, field);
            } catch (IOException e) {
                log.error("Error getting field suggestions: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        });
    }

    private SearchRequest buildFieldSuggestionRequest(Long teamId, String field, String prefix) {
        return SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .query(q -> q
                        .bool(b -> b
                                .must(m -> m
                                        .term(t -> t
                                                .field(FIELD_TEAM_ID)
                                                .value(teamId)))
                                .must(m -> m
                                        .prefix(p -> p
                                                .field(field + ".keyword")
                                                .value(prefix)))))
                .aggregations(field + "_suggestions", a -> a
                        .terms(t -> t
                                .field(field + ".keyword")
                                .size(5))));
    }

    @Async
    public void indexTask(Task task) {
        try {
            TaskDocument document = convertToDocument(task);
            IndexRequest<TaskDocument> request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(task.getId().toString())
                    .document(document));

            IndexResponse response = elasticsearchClient.index(request);
            log.debug("Task indexed successfully: {}", response.result());
        } catch (Exception e) {
            log.error("Error indexing task {}: {}", task.getId(), e.getMessage(), e);
        }
    }

    @Async
    public void deleteTaskIndex(Long taskId) {
        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(INDEX_NAME)
                    .id(taskId.toString()));

            DeleteResponse response = elasticsearchClient.delete(request);
            log.debug("Task index deleted successfully: {}", response.result());
        } catch (Exception e) {
            log.error("Error deleting task index {}: {}", taskId, e.getMessage(), e);
        }
    }

    private TaskDocument convertToDocument(Task task) {
        List<TaskComment> comments = taskCommentRepository.findByTaskIdOrderByCreatedAtDesc(task.getId());
        String commentText = comments.stream()
                .map(TaskComment::getContent)
                .collect(Collectors.joining(" "));

        return TaskDocument.builder()
                .taskId(task.getId())
                .teamId(task.getTeam().getId())
                .name(task.getName())
                .description(task.getDescription())
                .status(task.getStatus().name())
                .priority(task.getPriority().name())
                .assignedUserId(task.getAssignedUser() != null ? task.getAssignedUser().getId() : null)
                .labels(task.getLabels())
                .dueDate(task.getDueDate())
                .estimatedEffort(task.getEstimatedEffort())
                .commentText(commentText)
                .suggest(createSuggestInput(task))
                .build();
    }

    private List<String> createSuggestInput(Task task) {
        Set<String> suggestions = new LinkedHashSet<>();
        suggestions.add(task.getName());

        String[] words = task.getName().split("\\s+");
        if (words.length > 1) {
            suggestions.addAll(Arrays.asList(words));
        }

        return new ArrayList<>(suggestions);
    }

    private TaskSearchResponse mapToSearchResponse(Hit<TaskDocument> hit) {
        TaskDocument doc = hit.source();
        if (doc == null) {
            return null;
        }
        Map<String, List<String>> highlights = hit.highlight();

        return TaskSearchResponse.builder()
                .taskId(doc.getTaskId())
                .name(getHighlightedField(highlights, FIELD_NAME, doc.getName()))
                .description(getHighlightedField(highlights, FIELD_DESCRIPTION, doc.getDescription()))
                .status(Task.TaskStatus.valueOf(doc.getStatus()))
                .priority(Task.TaskPriority.valueOf(doc.getPriority()))
                .assignedUserId(doc.getAssignedUserId())
                .labels(doc.getLabels())
                .dueDate(doc.getDueDate())
                .estimatedEffort(doc.getEstimatedEffort())
                .highlights(highlights)
                .build();
    }

    private String getHighlightedField(Map<String, List<String>> highlights,
            String field, String defaultValue) {
        if (highlights != null && highlights.containsKey(field) &&
                !highlights.get(field).isEmpty()) {
            return highlights.get(field).get(0);
        }
        return defaultValue;
    }

    private List<String> extractSuggestions(SearchResponse<TaskDocument> response) {
        return Optional.ofNullable(response.suggest())
                .map(suggest -> suggest.get("task_suggestions"))
                .map(suggestions -> suggestions.stream()
                        .flatMap(suggestion -> suggestion.completion().options().stream())
                        .map(option -> option.text())
                        .distinct()
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private List<String> extractFieldSuggestions(SearchResponse<TaskDocument> response,
            String field) {
        return Optional.ofNullable(response.aggregations())
                .map(aggs -> aggs.get(field + "_suggestions"))
                .map(agg -> agg.sterms().buckets().array())
                .map(buckets -> buckets.stream()
                        .map(bucket -> bucket.key().stringValue())
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private String generateSearchCacheKey(Long teamId, TaskSearchRequest request,
            Pageable pageable) {
        return String.format("search:%d:%s:%d:%d",
                teamId,
                request.hashCode(),
                pageable.getPageNumber(),
                pageable.getPageSize());
    }

    @Async
    public void refreshIndex() {
        try {
            elasticsearchClient.indices().refresh(r -> r.index(INDEX_NAME));
        } catch (IOException e) {
            log.error("Error refreshing index: {}", e.getMessage(), e);
        }
    }
}
