package io.shinmen.app.tasker.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class TaskCacheConfig {

    private static final String TASK_CACHE = "taskCache";
    private static final String TEAM_TASKS_CACHE = "teamTasksCache";
    private static final String USER_TASKS_CACHE = "userTasksCache";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .prefixCacheNameWith("tasker:")
                .entryTtl(Duration.ofMinutes(30));


        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();


        cacheConfigurations.put(TASK_CACHE, defaultConfig);


        cacheConfigurations.put(TEAM_TASKS_CACHE,
                defaultConfig.entryTtl(Duration.ofMinutes(15)));


        cacheConfigurations.put(USER_TASKS_CACHE,
                defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
