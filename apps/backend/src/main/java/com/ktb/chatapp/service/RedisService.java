package com.ktb.chatapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.FileResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.redis.RedisMessagePage;
import com.ktb.chatapp.service.redis.RedisMessageReactionStore;
import com.ktb.chatapp.service.redis.RedisMessageReaderStore;
import com.ktb.chatapp.service.redis.RedisMessageStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {
    private final UserRepository userRepository;
    private final RedisMessageStore redisMessageStore;
    private final ObjectMapper objectMapper;
    private final FileRepository fileRepository;
    private final RedisMessageReactionStore redisMessageReactionStore;
    private final RedisMessageReaderStore redisMessageReaderStore;

    public FetchMessagesResponse loadMessages(String roomId, int limit, LocalDateTime before, String userId) {
        // 메시지 조회
        RedisMessagePage page = redisMessageStore.findMessagesByPaging(roomId, limit, before);
        List<Message> messages = toMessage(page.messages());

        var msgIds = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toSet());

        if(messages.isEmpty()) {
            return FetchMessagesResponse.builder()
                    .messages(List.of())
                    .hasMore(false)
                    .build();
        }

        Map<String, User> senderUserMap = preloadSenders(messages);
        Map<String, File> senderFileMap = preloadFiles(messages);

        // 메시지 읽음 처리
        redisMessageReaderStore.saveReader(msgIds, userId, LocalDateTime.now());

        Map<String, Map<String, Set<String>>> reactionsByIds = redisMessageReactionStore.findReactionsByIds(msgIds);
        Map<String, Map<String, Long>> readersByIds = redisMessageReaderStore.findReadersByIds(msgIds);

        // 메시지 응답 dto 매핑 처리
        List<MessageResponse> responses = toMessageResponse(messages, senderUserMap, senderFileMap);
        for (var response : responses) {
            response.setReactions(reactionsByIds.get(response.getId()));

            Map<String, Long> readerMap = readersByIds.get(response.getId());
            if (readerMap != null && !readerMap.isEmpty()) {
                List<Message.MessageReader> readers = readerMap.entrySet().stream()
                        .map(entry -> {
                            String userIdx = entry.getKey();
                            Long epochMillis = entry.getValue();
                            LocalDateTime readAt = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(epochMillis),
                                    ZoneId.systemDefault()
                            );
                            return new Message.MessageReader(userIdx, readAt);
                        })
                        .toList();
                response.setReaders(readers);
            }
        }

        return FetchMessagesResponse.builder()
                .messages(responses)
                .hasMore(page.hasNext())
                .build();
    }

    public Message findMessageByFileId(String fileId) {
        String messageId = redisMessageStore.findMessageIdByFileId(fileId);
        if (messageId == null) {
            return null;
        }
        return redisMessageStore.findById(messageId);
    }

    // db 없이 id 생성을 위해 UUID로 아이디 생성
    public Message save(Message message) {
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }

        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }
        redisMessageStore.save(message);

        if (message.getFileId() != null) {
            redisMessageStore.linkFileToMessage(message.getFileId(), message.getId());
        }
        return message;
    }

    private List<Message> toMessage(List<String> serializedMessages) {
        if (serializedMessages == null || serializedMessages.isEmpty()) {
            return List.of();
        }

        List<Message> result = new ArrayList<>(serializedMessages.size());

        for (String json : serializedMessages) {
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                Message message = objectMapper.readValue(json, Message.class);

                result.add(message);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize message JSON from Redis", e);
            }
        }

        return result;
    }

    private List<MessageResponse> toMessageResponse(List<Message> messages,
                                                    Map<String, User> senderUserMap,
                                                    Map<String, File> senderFileMap) {
        List<MessageResponse> result = new ArrayList<>(messages.size());

        for (Message message : messages) {
            if (message == null) {
                continue;
            }

            MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                    .id(message.getId())
                    .content(message.getContent())
                    .type(message.getType())
                    .timestamp(message.toTimestampMillis())
                    .roomId(message.getRoomId())
                    .reactions(message.getReactions() != null ? message.getReactions() : new HashMap<>())
                    .readers(message.getReaders() != null ? message.getReaders() : new ArrayList<>());

            // sender 매핑 (User → UserResponse)
            if (message.getSenderId() != null && senderUserMap != null) {
                User sender = senderUserMap.get(message.getSenderId());
                if (sender != null) {
                    builder.sender(UserResponse.builder()
                            .id(sender.getId())
                            .name(sender.getName())
                            .email(sender.getEmail())
                            .profileImage(sender.getProfileImage())
                            .build());
                }
            }

            // file 매핑 (File → FileResponse)
            if (message.getFileId() != null && senderFileMap != null) {
                File file = senderFileMap.get(message.getFileId());
                if (file != null) {
                    builder.file(FileResponse.builder()
                            .id(file.getId())
                            .filename(file.getFilename())
                            .originalname(file.getOriginalname())
                            .mimetype(file.getMimetype())
                            .size(file.getSize())
                            .build());
                }
            }

            // metadata 매핑
            if (message.getMetadata() != null) {
                builder.metadata(message.getMetadata());
            }

            result.add(builder.build());
        }

        return result;
    }

    private Map<String, User> preloadSenders(List<Message> messages) {
        Set<String> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (senderIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private Map<String, File> preloadFiles(List<Message> messages) {
        Set<String> fileIds = messages.stream()
                .map(Message::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (fileIds.isEmpty()) {
            return Map.of();
        }

        return fileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(File::getId, file -> file));
    }

    public Message handleMessageReaction(String messageId, String reaction, String type, String userId) {
        if (Objects.equals(type, "add")) {
            redisMessageReactionStore.addReaction(messageId, reaction, userId);
        } else if (Objects.equals(type, "remove")) {
            redisMessageReactionStore.removeReaction(messageId, reaction, userId);
        } else {
            return null;
        }

        Message message = redisMessageStore.findById(messageId);
        if (message == null) {
            return null;
        }

        Map<String, Map<String, Set<String>>> reactionsByIds = redisMessageReactionStore.findReactionsByIds(Set.of(messageId));
        Map<String, Set<String>> reactions = reactionsByIds.getOrDefault(messageId, Map.of());
        message.setReactions(reactions);

        return message;
    }

    public boolean handleMessageRead(List<String> messageIds, String userId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return false;
        }
        if (userId == null || userId.isBlank()) {
            return false;
        }

        try {
            Set<String> messageIdSet = new java.util.HashSet<>(messageIds);
            redisMessageReaderStore.saveReader(messageIdSet, userId, LocalDateTime.now());
            return true;
        } catch (Exception e) {
            log.error("Failed to handle message read in Redis. Fallback to DB.", e);
            return false;
        }
    }
}
