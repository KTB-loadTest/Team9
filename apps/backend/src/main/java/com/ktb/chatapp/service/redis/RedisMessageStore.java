package com.ktb.chatapp.service.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageStore {
    private static final String TIMELINE_KEY_PATTERN = "room:%s:timeline";
    private static final String MESSAGE_BODY_KEY_PATTERN = "msg:%s:body";
    private static final String FILE_MESSAGE_KEY_PATTERN = "file:%s:message";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisMessageReactionStore redisMessageReactionStore;
    private final RedisMessageReaderStore redisMessageReaderStore;

    public Message findById(String messageId) {
        String key = MESSAGE_BODY_KEY_PATTERN.formatted(messageId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize message JSON");
        }
    }

    public void linkFileToMessage(String fileId, String messageId) {
        String key = FILE_MESSAGE_KEY_PATTERN.formatted(fileId);
        redisTemplate.opsForValue().set(key, messageId);
    }

    public String findMessageIdByFileId(String fileId) {
        String key = FILE_MESSAGE_KEY_PATTERN.formatted(fileId);
        return redisTemplate.opsForValue().get(key);
    }

    public RedisMessagePage findMessagesByPaging(String roomId, int limit, LocalDateTime before) {
        // roomId와 limit, before 로 해당하는 message ids 조회
        String key = TIMELINE_KEY_PATTERN.formatted(roomId);

        long maxScore = before.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        Set<String> ids = redisTemplate.opsForZSet()
                .reverseRangeByScore(key, maxScore, Long.MIN_VALUE, 0, limit + 1);

        if (ids == null || ids.isEmpty()) {
            return new RedisMessagePage(List.of(), false);
        }

        boolean hasNext = ids.size() > limit;

        // 얻은 message ids로 실제 message body 값 조회
        List<String> msgs = findMessagesByIds(ids);

        return new RedisMessagePage(msgs, hasNext);
    }

    private List<String> findMessagesByIds(Set<String> messageIds) {
        List<String> messages = new ArrayList<>(messageIds.size());

        for (String messageId : messageIds) {
            String key = MESSAGE_BODY_KEY_PATTERN.formatted(messageId);
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                messages.add(value);
            }
        }

        return messages;
    }

    public void save(Message message) {
        String key = MESSAGE_BODY_KEY_PATTERN.formatted(message.getId());
        try {
            String body = objectMapper.writeValueAsString(message);
            redisTemplate.opsForValue().set(key, body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize message to JSON for Redis");
        }

        String timelineKey = TIMELINE_KEY_PATTERN.formatted(message.getRoomId());
        long score = message.toTimestampMillis();
        redisTemplate.opsForZSet().add(timelineKey, message.getId(), (double) score);
    }
}
