package com.ktb.chatapp.service.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisMessageReaderStore {
    private static final String MESSAGE_READERS_KEY_PATTERN = "msg:%s:readers";

    private final StringRedisTemplate redisTemplate;

    public Map<String, Map<String, Long>> findReadersByIds(Set<String> messageIds) {
        Map<String, Map<String, Long>> result = new HashMap<>();

        if (messageIds == null || messageIds.isEmpty()) {
            return result;
        }

        for (String messageId : messageIds) {
            String key = MESSAGE_READERS_KEY_PATTERN.formatted(messageId);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries == null || entries.isEmpty()) {
                continue;
            }

            Map<String, Long> readersForMessage = new HashMap<>();

            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                Object field = entry.getKey();
                Object value = entry.getValue();
                if (field == null || value == null) {
                    continue;
                }
                String userId = field.toString();
                Long epochMillis;
                if (value instanceof Long l) {
                    epochMillis = l;
                } else {
                    try {
                        epochMillis = Long.parseLong(value.toString());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
                readersForMessage.put(userId, epochMillis);
            }

            if (!readersForMessage.isEmpty()) {
                result.put(messageId, readersForMessage);
            }
        }

        return result;
    }

    public void saveReader(Set<String> messageIds, String userId, LocalDateTime readAt) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        for (var messageId : messageIds) {
            saveReader(messageId, userId, readAt);
        }
    }

    public void saveReader(String messageId, String userId, LocalDateTime readAt) {
        String key = MESSAGE_READERS_KEY_PATTERN.formatted(messageId);
        long epochMillis = readAt.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        redisTemplate.opsForHash().put(key, userId, Long.toString(epochMillis));
    }
}
