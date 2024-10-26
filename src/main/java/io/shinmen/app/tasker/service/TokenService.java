package io.shinmen.app.tasker.service;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import io.shinmen.app.tasker.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider tokenProvider;

    public void saveRefreshToken(String username, String refreshToken) {
        String key = "refresh_token:" + username;
        redisTemplate.opsForValue().set(key, refreshToken, 30, TimeUnit.DAYS);
    }

    public String getRefreshToken(String username) {
        String key = "refresh_token:" + username;
        Object token = redisTemplate.opsForValue().get(key);
        return token != null ? token.toString() : null;
    }

    public void invalidateTokens(Long username) {
        String refreshKey = "refresh_token:" + username;
        redisTemplate.delete(refreshKey);

        String blacklistKey = "token_blacklist:" + username;
        redisTemplate.opsForValue().set(blacklistKey, true, 1, TimeUnit.HOURS);
    }

    public boolean isTokenBlacklisted(String token) {
        String username = tokenProvider.getUsernameFromToken(token);
        String blacklistKey = "token_blacklist:" + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
    }
}
