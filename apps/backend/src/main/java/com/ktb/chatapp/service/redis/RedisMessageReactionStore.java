package com.ktb.chatapp.service.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisMessageReactionStore {
    private static final String MESSAGE_REACTION_KEY_PATTERN = "msg:%s:reaction:%s";

    private final StringRedisTemplate redisTemplate;

    public Map<String, Map<String, Set<String>>> findReactionsByIds(Set<String> messageIds) {
        Map<String, Map<String, Set<String>>> result = new HashMap<>();

        if (messageIds == null || messageIds.isEmpty()) {
            return result;
        }

        for (String messageId : messageIds) {
            String pattern = String.format("msg:%s:reaction:*", messageId);
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                continue;
            }

            Map<String, Set<String>> reactionsForMessage = new HashMap<>();

            for (String key : keys) {
                String reaction = extractReactionFromKey(key, messageId);
                Set<String> userIds = redisTemplate.opsForSet().members(key);
                reactionsForMessage.put(reaction, userIds != null ? userIds : Set.of());
            }

            result.put(messageId, reactionsForMessage);
        }

        return result;
    }

    private String extractReactionFromKey(String key, String messageId) {
        String prefix = "msg:" + messageId + ":reaction:";
        if (key.startsWith(prefix)) {
            return key.substring(prefix.length());
        }
        int idx = key.lastIndexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    public void addReaction(String messageId, String reaction, String userId) {
        String key = MESSAGE_REACTION_KEY_PATTERN.formatted(messageId, reaction);
        redisTemplate.opsForSet().add(key, userId);
    }

    public void removeReaction(String messageId, String reaction, String userId) {
        String key = MESSAGE_REACTION_KEY_PATTERN.formatted(messageId, reaction);
        redisTemplate.opsForSet().remove(key, userId);
    }
}
