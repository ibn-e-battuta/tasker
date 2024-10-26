package io.shinmen.app.tasker.service;

import io.shinmen.app.tasker.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class TaskCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    private static final String TASK_CACHE = "taskCache";
    private static final String TEAM_TASKS_CACHE = "teamTasksCache";
    private static final String USER_TASKS_CACHE = "userTasksCache";
    private static final String TASK_LOCK_PREFIX = "tasker:task:lock:";
    private static final String TASK_PREFIX = "tasker:task:";
    private static final String TEAM_TASKS_PREFIX = "tasker:teamTasks:";
    private static final String USER_TASKS_PREFIX = "tasker:userTasks:";
    private static final String LOCK_PREFIX = "tasker:lock:";
    private static final long TASK_CACHE_DURATION = 3600;
    private static final long LIST_CACHE_DURATION = 300;


    public TaskResponse getCachedTask(Long taskId) {
        return Objects.requireNonNull(cacheManager.getCache(TASK_CACHE))
                .get(taskId.toString(), TaskResponse.class);
    }

    public void cacheTeamTasks(Long teamId, String cacheKey, Page<TaskResponse> tasks) {
        Objects.requireNonNull(cacheManager.getCache(TEAM_TASKS_CACHE))
                .put(generateTeamTasksCacheKey(teamId, cacheKey), tasks);
    }

    public Page<TaskResponse> getCachedTeamTasks(Long teamId, String cacheKey) {
        return Objects.requireNonNull(cacheManager.getCache(TEAM_TASKS_CACHE))
                .get(generateTeamTasksCacheKey(teamId, cacheKey), Page.class);
    }

    public void cacheUserTasks(Long userId, List<TaskResponse> tasks) {
        Objects.requireNonNull(cacheManager.getCache(USER_TASKS_CACHE))
                .put(userId.toString(), tasks);
    }

    public List<TaskResponse> getCachedUserTasks(Long userId) {
        return Objects.requireNonNull(cacheManager.getCache(USER_TASKS_CACHE))
                .get(userId.toString(), List.class);
    }

    public void evictTaskCache(Long taskId) {
        Objects.requireNonNull(cacheManager.getCache(TASK_CACHE)).evict(taskId.toString());
    }

    public void evictTeamTasksCache(Long teamId) {
        Objects.requireNonNull(cacheManager.getCache(TEAM_TASKS_CACHE))
                .evict(teamId.toString());
    }

    public void evictUserTasksCache(Long userId) {
        Objects.requireNonNull(cacheManager.getCache(USER_TASKS_CACHE))
                .evict(userId.toString());
    }

    public boolean acquireTaskLock(Long taskId, String lockOwner) {
        String lockKey = TASK_LOCK_PREFIX + taskId;
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockOwner, 30, TimeUnit.SECONDS));
    }

    public void releaseTaskLock(Long taskId, String lockOwner) {
        String lockKey = TASK_LOCK_PREFIX + taskId;
        String currentLockOwner = (String) redisTemplate.opsForValue().get(lockKey);
        if (lockOwner.equals(currentLockOwner)) {
            redisTemplate.delete(lockKey);
        }
    }

    public void evictAllTaskCaches() {
        Objects.requireNonNull(cacheManager.getCache(TASK_CACHE)).clear();
        Objects.requireNonNull(cacheManager.getCache(TEAM_TASKS_CACHE)).clear();
        Objects.requireNonNull(cacheManager.getCache(USER_TASKS_CACHE)).clear();
    }

    private String generateTeamTasksCacheKey(Long teamId, String cacheKey) {
        return String.format("%d:%s", teamId, cacheKey);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void clearAllCaches() {
        evictAllTaskCaches();
    }

    public void cacheTask(TaskResponse task) {
        String key = TASK_PREFIX + task.getId();
        redisTemplate.opsForValue().set(key, task, TASK_CACHE_DURATION);
    }

    public TaskResponse getTaskFromCache(Long taskId) {
        String key = TASK_PREFIX + taskId;
        return (TaskResponse) redisTemplate.opsForValue().get(key);
    }

    public void invalidateTaskCache(Long taskId) {
        String key = TASK_PREFIX + taskId;
        redisTemplate.delete(key);
    }

    public void invalidateTeamTaskCache(Long teamId) {
        String pattern = TEAM_TASKS_PREFIX + teamId + ":*";
        deleteByPattern(pattern);
    }

    public void invalidateUserTaskCache(Long userId) {
        String pattern = USER_TASKS_PREFIX + userId + ":*";
        deleteByPattern(pattern);
    }

    private void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public boolean acquireLock(String lockName, String owner) {
        String key = LOCK_PREFIX + lockName;
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(key, owner, 30, TimeUnit.SECONDS)
        );
    }

    public void releaseLock(String lockName, String owner) {
        String key = LOCK_PREFIX + lockName;
        String currentOwner = (String) redisTemplate.opsForValue().get(key);
        if (owner.equals(currentOwner)) {
            redisTemplate.delete(key);
        }
    }

    public <T> T getOrComputeTeamTasks(String cacheKey, Supplier<T> supplier) {
        String key = TEAM_TASKS_PREFIX + cacheKey;
        T cached = (T) redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return cached;
        }

        T computed = supplier.get();
        redisTemplate.opsForValue().set(key, computed, LIST_CACHE_DURATION);
        return computed;
    }

    public <T> T getOrComputeUserTasks(String cacheKey, Supplier<T> supplier) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (T) cached;
        }

        T result = supplier.get();
        if (result != null) {
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(15));
        }
        return result;
    }
}
