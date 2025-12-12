package com.ktb.chatapp.service;

import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * 간단한 인메모리 사용자 캐시.
 * - TTL: 기본 5분
 * - 멀티 노드 환경에서는 로컬 캐시 히트율을 높여 DB round-trip을 줄이는 용도
 */
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final ConcurrentHashMap<String, CachedUser> cache = new ConcurrentHashMap<>();

    public User get(String userId) {
        if (userId == null) {
            return null;
        }
        CachedUser cached = cache.get(userId);
        if (cached != null && !cached.isExpired()) {
            return cached.user();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            cache.put(userId, new CachedUser(user, Instant.now().plus(DEFAULT_TTL)));
        }
        return user;
    }

    public Map<String, User> getAll(Collection<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }

        Map<String, User> result = new ConcurrentHashMap<>();

        // 캐시 히트는 즉시 반환
        userIds.forEach(id -> {
            CachedUser cached = cache.get(id);
            if (cached != null && !cached.isExpired()) {
                result.put(id, cached.user());
            }
        });

        // 캐시 미스만 DB 조회
        var missIds = userIds.stream()
                .filter(id -> !result.containsKey(id))
                .toList();

        if (!missIds.isEmpty()) {
            userRepository.findAllById(missIds).forEach(user -> {
                result.put(user.getId(), user);
                cache.put(user.getId(), new CachedUser(user, Instant.now().plus(DEFAULT_TTL)));
            });
        }

        return result;
    }

    public void evict(String userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private record CachedUser(User user, Instant expiresAt) {
        boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }
}
