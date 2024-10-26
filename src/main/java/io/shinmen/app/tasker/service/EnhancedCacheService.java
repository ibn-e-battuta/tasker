package io.shinmen.app.tasker.service;

import io.shinmen.app.tasker.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class EnhancedCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private static final String TASK_KEY = "task:%d";
    private static final String TEAM_TASKS_KEY = "team:%d:tasks";
    private static final String USER_TASKS_KEY = "user:%d:tasks";
    private static final String TASK_COMMENTS_KEY = "task:%d:comments";
    private static final String TASK_ATTACHMENTS_KEY = "task:%d:attachments";
    private static final String TEAM_LABELS_KEY = "team:%d:labels";
    private static final String TEAM_MEMBERS_KEY = "team:%d:members";
    private static final String SEARCH_RESULTS_KEY = "search:%s";

    private static final String LOCK_KEY = "lock:%s";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private final Map<String, String> activeLocks = new ConcurrentHashMap<>();

    public void cacheTask(TaskResponse task) {
        String key = String.format(TASK_KEY, task.getId());
        redisTemplate.opsForValue().set(key, task, Duration.ofMinutes(30));
    }

    public TaskResponse getCachedTask(Long taskId) {
        String key = String.format(TASK_KEY, taskId);
        return (TaskResponse) redisTemplate.opsForValue().get(key);
    }

    public void cacheTeamTasks(Long teamId, Page<TaskResponse> tasks) {
        String key = String.format(TEAM_TASKS_KEY, teamId);
        redisTemplate.opsForValue().set(key, tasks, Duration.ofMinutes(15));
    }

    public Page<TaskResponse> getCachedTeamTasks(Long teamId) {
        String key = String.format(TEAM_TASKS_KEY, teamId);
        return (Page<TaskResponse>) redisTemplate.opsForValue().get(key);
    }

    public void cacheTaskComments(Long taskId, List<TaskCommentResponse> comments) {
        String key = String.format(TASK_COMMENTS_KEY, taskId);
        redisTemplate.opsForValue().set(key, comments, Duration.ofMinutes(20));
    }

    public List<TaskCommentResponse> getCachedTaskComments(Long taskId) {
        String key = String.format(TASK_COMMENTS_KEY, taskId);
        return (List<TaskCommentResponse>) redisTemplate.opsForValue().get(key);
    }

    public void cacheTaskAttachments(Long taskId, List<TaskAttachmentResponse> attachments) {
        String key = String.format(TASK_ATTACHMENTS_KEY, taskId);
        redisTemplate.opsForValue().set(key, attachments, Duration.ofMinutes(30));
    }

    public List<TaskAttachmentResponse> getCachedTaskAttachments(Long taskId) {
        String key = String.format(TASK_ATTACHMENTS_KEY, taskId);
        return (List<TaskAttachmentResponse>) redisTemplate.opsForValue().get(key);
    }

    public void cacheTeamLabels(Long teamId, List<TeamLabelResponse> labels) {
        String key = String.format(TEAM_LABELS_KEY, teamId);
        redisTemplate.opsForValue().set(key, labels, Duration.ofHours(1));
    }

    public List<TeamLabelResponse> getCachedTeamLabels(Long teamId) {
        String key = String.format(TEAM_LABELS_KEY, teamId);
        return (List<TeamLabelResponse>) redisTemplate.opsForValue().get(key);
    }

    public void cacheSearchResults(String searchKey, Page<TaskSearchResponse> results) {
        String key = String.format(SEARCH_RESULTS_KEY, searchKey);
        redisTemplate.opsForValue().set(key, results, Duration.ofMinutes(5));
    }

    public Page<TaskSearchResponse> getCachedSearchResults(String searchKey) {
        String key = String.format(SEARCH_RESULTS_KEY, searchKey);
        return (Page<TaskSearchResponse>) redisTemplate.opsForValue().get(key);
    }

    public void invalidateTask(Long taskId) {
        String taskKey = String.format(TASK_KEY, taskId);
        redisTemplate.delete(taskKey);
    }

    public void invalidateTeamCache(Long teamId) {
        Set<String> keys = redisTemplate.keys(String.format("tasker:team:%d:*", teamId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void invalidateUserCache(Long userId) {
        Set<String> keys = redisTemplate.keys(String.format("tasker:user:%d:*", userId));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public boolean acquireLock(String lockName, String owner) {
        String key = String.format(LOCK_KEY, lockName);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, owner, LOCK_TIMEOUT);

        if (Boolean.TRUE.equals(acquired)) {
            activeLocks.put(lockName, owner);
            return true;
        }
        return false;
    }

    public void releaseLock(String lockName, String owner) {
        String key = String.format(LOCK_KEY, lockName);
        String currentOwner = (String) redisTemplate.opsForValue().get(key);

        if (owner.equals(currentOwner)) {
            redisTemplate.delete(key);
            activeLocks.remove(lockName);
        }
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredLocks() {
        activeLocks.forEach((lockName, owner) -> {
            String key = String.format(LOCK_KEY, lockName);
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                activeLocks.remove(lockName);
            }
        });
    }

    public <T> T executeWithCache(String cacheKey, Duration ttl, Function<Void, T> dataLoader) {
        T cachedResult = (T) redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        T result = dataLoader.apply(null);
        if (result != null) {
            redisTemplate.opsForValue().set(cacheKey, result, ttl);
        }
        return result;
    }

    public <T> T getOrCompute(String key, Duration ttl, Supplier<T> supplier) {
        @SuppressWarnings("unchecked")
        T cached = (T) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        T value = supplier.get();
        if (value != null) {
            redisTemplate.opsForValue().set(key, value, ttl);
        }
        return value;
    }
}
