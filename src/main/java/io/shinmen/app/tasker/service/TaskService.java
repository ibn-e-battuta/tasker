package io.shinmen.app.tasker.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.shinmen.app.tasker.dto.SavedFilter;
import io.shinmen.app.tasker.dto.TaskCreateRequest;
import io.shinmen.app.tasker.dto.TaskFilter;
import io.shinmen.app.tasker.dto.TaskResponse;
import io.shinmen.app.tasker.dto.TaskSortCriteria;
import io.shinmen.app.tasker.dto.TaskStatistics;
import io.shinmen.app.tasker.dto.TaskUpdateRequest;
import io.shinmen.app.tasker.dto.UserResponse;
import io.shinmen.app.tasker.event.TaskEvent;
import io.shinmen.app.tasker.event.TaskEventPublisher;
import io.shinmen.app.tasker.exception.CustomException;
import io.shinmen.app.tasker.interfaces.TaskHistoryOperations;
import io.shinmen.app.tasker.interfaces.TaskNotificationOperations;
import io.shinmen.app.tasker.interfaces.TaskPermissionOperations;
import io.shinmen.app.tasker.model.Task;
import io.shinmen.app.tasker.model.Task.TaskPriority;
import io.shinmen.app.tasker.model.Task.TaskStatus;
import io.shinmen.app.tasker.model.Team;
import io.shinmen.app.tasker.model.TeamPermission;
import io.shinmen.app.tasker.model.User;
import io.shinmen.app.tasker.repository.SavedFilterEntity;
import io.shinmen.app.tasker.repository.SavedFilterRepository;
import io.shinmen.app.tasker.repository.TaskRepository;
import io.shinmen.app.tasker.repository.TeamMemberRepository;
import io.shinmen.app.tasker.repository.TeamRepository;
import io.shinmen.app.tasker.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskService implements TaskHistoryOperations, TaskNotificationOperations, TaskPermissionOperations {

    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkflowService workflowService;
    private final LabelService labelService;
    private final TaskCacheService cacheService;
    private final TaskEventPublisher eventPublisher;
    private final PermissionService permissionService;
    private final SavedFilterRepository savedFilterRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
    public TaskResponse createTask(Long teamId, Long userId, TaskCreateRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException("Team not found", HttpStatus.NOT_FOUND));

        permissionService.validatePermission(teamId, userId, TeamPermission.CREATE_TASK);

        User assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = validateAssignedUser(teamId, request.getAssignedUserId());
        }

        if (request.getLabels() != null && !request.getLabels().isEmpty()) {
            labelService.validateAndNormalizeLabels(teamId, request.getLabels());
        }

        Task task = Task.builder()
                .name(request.getName())
                .description(request.getDescription())
                .team(team)
                .assignedUser(assignedUser)
                .status(request.getStatus())
                .priority(request.getPriority())
                .dueDate(request.getDueDate())
                .estimatedEffort(request.getEstimatedEffort())
                .labels(request.getLabels() != null ? new HashSet<>(request.getLabels()) : new HashSet<>())
                .watchers(new HashSet<>())
                .locked(false)
                .finalStatus(false)
                .build();

        task = taskRepository.save(task);

        if (request.getWatcherIds() != null && !request.getWatcherIds().isEmpty()) {
            addWatchers(task, request.getWatcherIds(), userId);
        }

        eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_CREATED)
                .task(task)
                .actorUserId(userId)
                .build());

        TaskResponse response = mapTaskToResponse(task);
        cacheService.cacheTask(response);

        return response;
    }

    @Transactional
    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
    public TaskResponse updateTask(Long taskId, Long userId, TaskUpdateRequest request) {
        String lockKey = "task_lock:" + taskId;
        try {
            if (!cacheService.acquireLock(lockKey, userId.toString())) {
                throw new CustomException(
                        "Task is currently being edited by another user",
                        HttpStatus.CONFLICT);
            }

            Task task = getTaskWithLockCheck(taskId);
            validateTaskVersion(task, request.getVersion());
            permissionService.validatePermission(task.getTeam().getId(), userId, TeamPermission.EDIT_TASK);

            if (workflowService.isFinalStatus(task.getTeam().getId(), task.getStatus())) {
                throw new CustomException(
                        "Task is in final status. Status reversion required to make changes.",
                        HttpStatus.BAD_REQUEST);
            }

            Map<String, Object> oldValues = captureOldValues(task);

            task.setName(request.getName());
            task.setDescription(request.getDescription());
            task.setPriority(request.getPriority());
            task.setDueDate(request.getDueDate());
            task.setEstimatedEffort(request.getEstimatedEffort());

            if (!task.getStatus().equals(request.getStatus())) {
                handleStatusChange(task, request.getStatus(), userId);
            }

            if (!Objects.equals(
                    task.getAssignedUser() != null ? task.getAssignedUser().getId() : null,
                    request.getAssignedUserId())) {
                handleAssignmentChange(task, request.getAssignedUserId(), userId);
            }

            if (request.getLabels() != null) {
                handleLabelChanges(task, request.getLabels(), userId);
            }

            task = taskRepository.save(task);

            Map<String, Object> newValues = captureNewValues(task);
            publishUpdateEvent(task, userId, oldValues, newValues);

            TaskResponse response = mapTaskToResponse(task);
            cacheService.cacheTask(response);
            cacheService.invalidateTeamTaskCache(task.getTeam().getId());

            if (task.getAssignedUser() != null) {
                cacheService.invalidateUserTaskCache(task.getAssignedUser().getId());
            }

            return response;

        } finally {
            cacheService.releaseLock(lockKey, userId.toString());
        }
    }

    @Override
    public void recordTaskChanges(Task task, Long userId, Map<String, Object> oldValues,
            Map<String, Object> newValues) {
        oldValues.forEach((field, oldValue) -> {
            Object newValue = newValues.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                recordTaskHistory(task, userId, "TASK_UPDATED", oldValue, newValue,
                        Map.of("field", field));
            }
        });
    }

    private void handleStatusChange(Task task, Task.TaskStatus newStatus, Long userId) {
        Task.TaskStatus oldStatus = task.getStatus();

        workflowService.validateStatusTransition(
                task.getTeam().getId(),
                oldStatus,
                newStatus);

        task.setStatus(newStatus);

        if (workflowService.isFinalStatus(task.getTeam().getId(), newStatus)) {
            task.setFinalStatus(true);
        }

        eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_STATUS_CHANGED)
                .task(task)
                .actorUserId(userId)
                .data(Map.of(
                        "oldStatus", oldStatus,
                        "newStatus", newStatus))
                .build());
    }

    private void handleAssignmentChange(Task task, Long newAssigneeId, Long userId) {
        User oldAssignee = task.getAssignedUser();
        User newAssignee = newAssigneeId != null ? validateAssignedUser(task.getTeam().getId(), newAssigneeId) : null;

        task.setAssignedUser(newAssignee);

        eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_ASSIGNED)
                .task(task)
                .actorUserId(userId)
                .data(Map.of(
                        "oldAssigneeId", oldAssignee != null ? oldAssignee.getId() : null,
                        "newAssigneeId", newAssignee != null ? newAssignee.getId() : null))
                .build());
    }

    private void handleLabelChanges(Task task, Set<String> newLabels, Long userId) {
        labelService.validateAndNormalizeLabels(task.getTeam().getId(), newLabels);

        Set<String> oldLabels = new HashSet<>(task.getLabels());
        task.setLabels(newLabels);

        Set<String> addedLabels = new HashSet<>(newLabels);
        addedLabels.removeAll(oldLabels);

        Set<String> removedLabels = new HashSet<>(oldLabels);
        removedLabels.removeAll(newLabels);

        if (!addedLabels.isEmpty()) {
            eventPublisher.publish(TaskEvent.builder()
                    .type(TaskEvent.EventType.LABEL_ADDED)
                    .task(task)
                    .actorUserId(userId)
                    .data(Map.of("labels", addedLabels))
                    .build());
        }

        if (!removedLabels.isEmpty()) {
            eventPublisher.publish(TaskEvent.builder()
                    .type(TaskEvent.EventType.LABEL_REMOVED)
                    .task(task)
                    .actorUserId(userId)
                    .data(Map.of("labels", removedLabels))
                    .build());
        }
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        Task task = getTaskWithLockCheck(taskId);

        permissionService.validatePermission(
                task.getTeam().getId(),
                userId,
                TeamPermission.DELETE_TASK);

        eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.TASK_DELETED)
                .task(task)
                .actorUserId(userId)
                .build());

        taskRepository.delete(task);

        cacheService.invalidateTaskCache(taskId);
        cacheService.invalidateTeamTaskCache(task.getTeam().getId());
        if (task.getAssignedUser() != null) {
            cacheService.invalidateUserTaskCache(task.getAssignedUser().getId());
        }
    }

    @Transactional
    public void updateWatchers(Long taskId, Long userId, Set<Long> watcherIds) {
        Task originalTask = getTaskWithLockCheck(taskId);

        permissionService.validatePermission(
                originalTask.getTeam().getId(),
                userId,
                TeamPermission.MANAGE_WATCHERS);

        Set<User> currentWatchers = new HashSet<>(originalTask.getWatchers());
        Set<User> newWatchers = watcherIds.stream()
                .map(watcherId -> validateTeamMember(originalTask.getTeam().getId(), watcherId))
                .collect(Collectors.toSet());

        Set<User> addedWatchers = new HashSet<>(newWatchers);
        addedWatchers.removeAll(currentWatchers);

        Set<User> removedWatchers = new HashSet<>(currentWatchers);
        removedWatchers.removeAll(newWatchers);

        originalTask.setWatchers(newWatchers);
        final Task savedTask = taskRepository.save(originalTask);

        addedWatchers.forEach(watcher -> eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.WATCHER_ADDED)
                .task(savedTask)
                .actorUserId(userId)
                .data(Map.of("watcherId", watcher.getId()))
                .build()));

        removedWatchers.forEach(watcher -> eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.WATCHER_REMOVED)
                .task(savedTask)
                .actorUserId(userId)
                .data(Map.of("watcherId", watcher.getId()))
                .build()));

        cacheService.cacheTask(mapTaskToResponse(savedTask));
    }

    private User validateAssignedUser(Long teamId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new CustomException("User is not a member of the team", HttpStatus.BAD_REQUEST);
        }

        return user;
    }

    private User validateTeamMember(Long teamId, Long userId) {
        return validateAssignedUser(teamId, userId);
    }

    private Task getTaskWithLockCheck(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        if (task.isLocked()) {
            throw new CustomException("Task is locked", HttpStatus.BAD_REQUEST);
        }

        return task;
    }

    private void validateTaskVersion(Task task, Long version) {
        if (!Objects.equals(task.getVersion(), version)) {
            throw new CustomException(
                    "Task has been modified by another user. Please refresh and try again.",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> captureOldValues(Task task) {
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("name", task.getName());
        oldValues.put("description", task.getDescription());
        oldValues.put("status", task.getStatus());
        oldValues.put("priority", task.getPriority());
        oldValues.put("dueDate", task.getDueDate());
        oldValues.put("estimatedEffort", task.getEstimatedEffort());
        oldValues.put("labels", new HashSet<>(task.getLabels()));
        oldValues.put("assignedUserId",
                task.getAssignedUser() != null ? task.getAssignedUser().getId() : null);
        oldValues.put("locked", task.isLocked());
        oldValues.put("finalStatus", task.isFinalStatus());
        return oldValues;
    }

    private Map<String, Object> captureNewValues(Task task) {
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("name", task.getName());
        newValues.put("description", task.getDescription());
        newValues.put("status", task.getStatus());
        newValues.put("priority", task.getPriority());
        newValues.put("dueDate", task.getDueDate());
        newValues.put("estimatedEffort", task.getEstimatedEffort());
        newValues.put("labels", new HashSet<>(task.getLabels()));
        newValues.put("assignedUserId",
                task.getAssignedUser() != null ? task.getAssignedUser().getId() : null);
        newValues.put("locked", task.isLocked());
        newValues.put("finalStatus", task.isFinalStatus());
        return newValues;
    }

    private void publishUpdateEvent(Task task, Long userId,
            Map<String, Object> oldValues, Map<String, Object> newValues) {
        oldValues.forEach((field, oldValue) -> {
            Object newValue = newValues.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                eventPublisher.publish(TaskEvent.builder()
                        .type(TaskEvent.EventType.TASK_UPDATED)
                        .task(task)
                        .actorUserId(userId)
                        .data(Map.of(
                                "field", field,
                                "oldValue", oldValue,
                                "newValue", newValue))
                        .build());
            }
        });
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId, Long userId) {
        TaskResponse cachedTask = cacheService.getTaskFromCache(taskId);
        if (cachedTask != null) {
            permissionService.validatePermission(
                    cachedTask.getTeamId(),
                    userId,
                    TeamPermission.VIEW_TASK);
            return cachedTask;
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        permissionService.validatePermission(
                task.getTeam().getId(),
                userId,
                TeamPermission.VIEW_TASK);

        TaskResponse response = mapTaskToResponse(task);
        cacheService.cacheTask(response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> findTasks(Long teamId, Long userId,
            TaskFilter filter, List<TaskSortCriteria> sortCriteria, Pageable pageable) {

        permissionService.validatePermission(teamId, userId, TeamPermission.VIEW_TASK);

        String cacheKey = generateFilterCacheKey(teamId, filter, sortCriteria, pageable);

        return cacheService.getOrComputeTeamTasks(cacheKey, () -> {
            Specification<Task> spec = buildTaskSpecification(teamId, filter);
            Sort sort = buildSort(sortCriteria);

            PageRequest pageRequest = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    sort);

            Page<Task> tasks = taskRepository.findAll(spec, pageRequest);
            return tasks.map(this::mapTaskToResponse);
        });
    }

    private void addWatchers(Task task, Set<Long> watcherIds, Long userId) {
        Set<User> watchers = watcherIds.stream()
                .map(watcherId -> validateTeamMember(task.getTeam().getId(), watcherId))
                .collect(Collectors.toSet());

        task.setWatchers(watchers);
        taskRepository.save(task);

        recordTaskHistory(task, userId, "WATCHERS_ADDED", null,
                watchers.stream().map(User::getId).collect(Collectors.toSet()), null);

        watchers.forEach(watcher -> eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.WATCHER_ADDED)
                .task(task)
                .actorUserId(userId)
                .data(Map.of("watcherId", watcher.getId()))
                .build()));
    }

    private Specification<Task> buildTaskSpecification(Long teamId, TaskFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("team").get("id"), teamId));
            predicates.add(cb.equal(root.get("deleted"), false));

            if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.getStatuses()));
            }

            if (filter.getPriorities() != null && !filter.getPriorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.getPriorities()));
            }

            if (filter.getAssignedUserIds() != null && !filter.getAssignedUserIds().isEmpty()) {
                predicates.add(root.get("assignedUser").get("id").in(filter.getAssignedUserIds()));
            }

            if (filter.getLabels() != null && !filter.getLabels().isEmpty()) {
                filter.getLabels().forEach(label -> predicates.add(cb.isMember(label, root.get("labels"))));
            }

            if (filter.getDueDateStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("dueDate"),
                        filter.getDueDateStart()));
            }

            if (filter.getDueDateEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("dueDate"),
                        filter.getDueDateEnd()));
            }

            if (filter.getSearchTerm() != null && !filter.getSearchTerm().trim().isEmpty()) {
                String searchTerm = "%" + filter.getSearchTerm().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), searchTerm),
                        cb.like(cb.lower(root.get("description")), searchTerm)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(List<TaskSortCriteria> sortCriteria) {
        if (sortCriteria == null || sortCriteria.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }

        List<Sort.Order> orders = sortCriteria.stream()
                .map(criteria -> new Sort.Order(
                        Sort.Direction.valueOf(criteria.getDirection().name()),
                        criteria.getField()))
                .collect(Collectors.toList());

        return Sort.by(orders);
    }

    @Transactional
    public SavedFilter saveUserFilter(Long teamId, Long userId, String filterName, TaskFilter filter) {
        permissionService.validatePermission(teamId, userId, TeamPermission.VIEW_TASK);

        if (savedFilterRepository.existsByUserIdAndTeamIdAndName(userId, teamId, filterName)) {
            throw new CustomException("Filter with this name already exists", HttpStatus.BAD_REQUEST);
        }

        SavedFilterEntity savedFilter = SavedFilterEntity.builder()
                .user(userRepository.getReferenceById(userId))
                .team(teamRepository.getReferenceById(teamId))
                .name(filterName)
                .filterJson(serializeFilter(filter))
                .build();

        savedFilter = savedFilterRepository.save(savedFilter);

        return mapToSavedFilter(savedFilter);
    }

    private String serializeFilter(TaskFilter filter) {
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (JsonProcessingException e) {
            throw new CustomException("Failed to serialize filter", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SavedFilter mapToSavedFilter(SavedFilterEntity entity) {
        try {
            TaskFilter filter = objectMapper.readValue(entity.getFilterJson(), TaskFilter.class);
            return SavedFilter.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .filter(filter)
                    .userId(entity.getUser().getId())
                    .teamId(entity.getTeam().getId())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        } catch (JsonProcessingException e) {
            throw new CustomException("Failed to deserialize filter", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void validateTeamPermission(Long teamId, Long userId, TeamPermission permission) {
        permissionService.validatePermission(teamId, userId, permission);
    }

    @Override
    public void notifyWatchers(Task task, Long actorUserId, String action) {
        eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.valueOf(action))
                .task(task)
                .actorUserId(actorUserId)
                .build());
    }

    @Override
    public void recordTaskHistory(Task task, Long userId, String action,
            Object oldValue, Object newValue, Map<String, Object> additionalInfo) {
        eventPublisher.publish(TaskEvent.builder()
                .type(TaskEvent.EventType.valueOf(action))
                .task(task)
                .actorUserId(userId)
                .data(buildHistoryData(oldValue, newValue, additionalInfo))
                .build());
    }

    private Map<String, Object> buildHistoryData(Object oldValue, Object newValue,
            Map<String, Object> additionalInfo) {
        Map<String, Object> data = new HashMap<>();
        if (oldValue != null)
            data.put("oldValue", oldValue);
        if (newValue != null)
            data.put("newValue", newValue);
        if (additionalInfo != null)
            data.putAll(additionalInfo);
        return data;
    }

    private TaskResponse mapTaskToResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .teamId(task.getTeam().getId())
                .name(task.getName())
                .description(task.getDescription())
                .assignedUser(task.getAssignedUser() != null ? mapUserToResponse(task.getAssignedUser()) : null)
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .estimatedEffort(task.getEstimatedEffort())
                .labels(task.getLabels())
                .watchers(task.getWatchers().stream()
                        .map(this::mapUserToResponse)
                        .collect(Collectors.toSet()))
                .locked(task.isLocked())
                .finalStatus(task.isFinalStatus())
                .version(task.getVersion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    public UserResponse mapUserToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .build();
    }

    private String generateFilterCacheKey(Long teamId, TaskFilter filter,
            List<TaskSortCriteria> sort, Pageable pageable) {
        return String.format("filter:%d:%s:%s:%d:%d",
                teamId,
                filter.hashCode(),
                sort != null ? sort.hashCode() : "null",
                pageable.getPageNumber(),
                pageable.getPageSize());
    }

    public boolean isTeamMember(Long teamId, Long userId) {
        return teamRepository.existsByIdAndMembersId(teamId, userId);
    }

    @Transactional
    public void lockTask(Long taskId, Long userId) {
        Task task = getTaskWithLockCheck(taskId);

        if (!permissionService.validateTeamOwnerOrAdmin(task.getTeam().getId(), userId)) {
            throw new CustomException("Only team owner or admin can lock tasks", HttpStatus.FORBIDDEN);
        }

        task.setLocked(true);
        taskRepository.save(task);

        recordTaskHistory(task, userId, "TASK_LOCKED", false, true, null);
        notifyWatchers(task, userId, "TASK_LOCKED");

        TaskResponse response = mapTaskToResponse(task);
        cacheService.cacheTask(response);
    }

    @Transactional
    public void unlockTask(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException("Task not found", HttpStatus.NOT_FOUND));

        if (!permissionService.validateTeamOwnerOrAdmin(task.getTeam().getId(), userId)) {
            throw new CustomException("Only team owner or admin can unlock tasks", HttpStatus.FORBIDDEN);
        }

        task.setLocked(false);
        taskRepository.save(task);

        recordTaskHistory(task, userId, "TASK_UNLOCKED", true, false, null);
        notifyWatchers(task, userId, "TASK_UNLOCKED");

        TaskResponse response = mapTaskToResponse(task);
        cacheService.cacheTask(response);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getTeamTasks(Long teamId, Long userId,
            TaskStatus status, TaskPriority priority,
            Long assignedUserId, String label, Pageable pageable) {

        permissionService.validatePermission(teamId, userId, TeamPermission.VIEW_TASK);

        String cacheKey = String.format("team-tasks:%d:%s:%s:%s:%s:%d:%d",
                teamId,
                status != null ? status.toString() : "null",
                priority != null ? priority.toString() : "null",
                assignedUserId != null ? assignedUserId.toString() : "null",
                label != null ? label : "null",
                pageable.getPageNumber(),
                pageable.getPageSize());

        return cacheService.getOrComputeTeamTasks(cacheKey, () -> {
            Specification<Task> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("team").get("id"), teamId));

                if (status != null) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
                if (priority != null) {
                    predicates.add(cb.equal(root.get("priority"), priority));
                }
                if (assignedUserId != null) {
                    predicates.add(cb.equal(root.get("assignedUser").get("id"), assignedUserId));
                }
                if (label != null) {
                    predicates.add(cb.isMember(label, root.get("labels")));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            Page<Task> tasks = taskRepository.findAll(spec, pageable);
            return tasks.map(this::mapTaskToResponse);
        });
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getUserTasks(Long userId) {
        return cacheService.getOrComputeUserTasks("user-tasks:" + userId,
                () -> taskRepository.findByAssignedUserIdAndDeletedFalse(userId).stream()
                        .map(this::mapTaskToResponse)
                        .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public TaskStatistics getTaskStatistics(Long teamId, Long userId, TaskFilter filter) {
        permissionService.validatePermission(teamId, userId, TeamPermission.VIEW_TASK);

        Specification<Task> spec = buildTaskSpecification(teamId, filter);
        List<Task> tasks = taskRepository.findAll(spec);

        return TaskStatistics.builder()
                .totalTasks((long) tasks.size())
                .tasksByStatus(calculateTasksByStatus(tasks))
                .tasksByPriority(calculateTasksByPriority(tasks))
                .tasksByLabel(calculateTasksByLabel(tasks))
                .tasksByAssignee(calculateTasksByAssignee(tasks))
                .averageCompletionTime(calculateAverageCompletionTime(tasks))
                .completionTimeByPriority(calculateCompletionTimeByPriority(tasks))
                .overdueTasks(calculateOverdueTasks(tasks))
                .overdueTasksByPriority(calculateOverdueTasksByPriority(tasks))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SavedFilter> getUserFilters(Long teamId, Long userId) {
        permissionService.validatePermission(teamId, userId, TeamPermission.VIEW_TASK);

        return savedFilterRepository.findByUserIdAndTeamId(userId, teamId)
                .stream()
                .map(this::mapToSavedFilter)
                .collect(Collectors.toList());
    }

    private Map<TaskStatus, Long> calculateTasksByStatus(List<Task> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
    }

    private Map<TaskPriority, Long> calculateTasksByPriority(List<Task> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(Task::getPriority, Collectors.counting()));
    }

    private Map<String, Long> calculateTasksByLabel(List<Task> tasks) {
        return tasks.stream()
                .flatMap(task -> task.getLabels().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private Map<Long, Long> calculateTasksByAssignee(List<Task> tasks) {
        return tasks.stream()
                .filter(task -> task.getAssignedUser() != null)
                .collect(Collectors.groupingBy(
                        task -> task.getAssignedUser().getId(),
                        Collectors.counting()));
    }

    private double calculateAverageCompletionTime(List<Task> tasks) {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE)
                .mapToLong(task -> ChronoUnit.HOURS.between(
                        task.getCreatedAt(),
                        task.getUpdatedAt()))
                .average()
                .orElse(0.0);
    }

    private Map<String, Double> calculateCompletionTimeByPriority(List<Task> tasks) {
        return tasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE)
                .collect(Collectors.groupingBy(
                        task -> task.getPriority().toString(),
                        Collectors.averagingLong(
                                task -> ChronoUnit.HOURS.between(task.getCreatedAt(), task.getUpdatedAt()))));
    }

    private int calculateOverdueTasks(List<Task> tasks) {
        LocalDateTime now = LocalDateTime.now();
        return (int) tasks.stream()
                .filter(task -> task.getDueDate() != null
                        && task.getDueDate().isBefore(now)
                        && task.getStatus() != TaskStatus.DONE
                        && task.getStatus() != TaskStatus.ARCHIVED)
                .count();
    }

    private Map<String, Integer> calculateOverdueTasksByPriority(List<Task> tasks) {
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .filter(task -> task.getDueDate() != null
                        && task.getDueDate().isBefore(now)
                        && task.getStatus() != TaskStatus.DONE
                        && task.getStatus() != TaskStatus.ARCHIVED)
                .collect(Collectors.groupingBy(
                        task -> task.getPriority().toString(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }
}
