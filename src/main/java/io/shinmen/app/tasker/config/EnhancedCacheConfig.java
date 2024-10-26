package io.shinmen.app.tasker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
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
@RequiredArgsConstructor
public class EnhancedCacheConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("taskCache", createCacheConfiguration(Duration.ofMinutes(30)));
        cacheConfigurations.put("taskListCache", createCacheConfiguration(Duration.ofMinutes(15)));
        cacheConfigurations.put("userTasksCache", createCacheConfiguration(Duration.ofMinutes(15)));
        cacheConfigurations.put("teamTasksCache", createCacheConfiguration(Duration.ofMinutes(15)));

        cacheConfigurations.put("taskCommentsCache", createCacheConfiguration(Duration.ofMinutes(20)));
        cacheConfigurations.put("recentCommentsCache", createCacheConfiguration(Duration.ofMinutes(5)));

        cacheConfigurations.put("taskAttachmentsCache", createCacheConfiguration(Duration.ofMinutes(30)));

        cacheConfigurations.put("teamLabelsCache", createCacheConfiguration(Duration.ofHours(1)));

        cacheConfigurations.put("teamMembersCache", createCacheConfiguration(Duration.ofHours(1)));

        cacheConfigurations.put("searchResultsCache", createCacheConfiguration(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(createCacheConfiguration(Duration.ofMinutes(30)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(objectMapper)))
                .prefixCacheNameWith("tasker:");
    }
}
