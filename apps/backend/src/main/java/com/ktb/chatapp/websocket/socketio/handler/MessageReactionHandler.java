package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.ktb.chatapp.dto.MessageReactionRequest;
import com.ktb.chatapp.dto.MessageReactionResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 메시지 리액션 처리 핸들러
 * 메시지 이모지 리액션 추가/제거 및 브로드캐스트 담당
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageReactionHandler {
    
    private final SocketIOServer socketIOServer;
    private final MongoTemplate mongoTemplate;
    
    @OnEvent(MESSAGE_REACTION)
    public void handleMessageReaction(SocketIOClient client, MessageReactionRequest data) {
        try {
            String userId = getUserId(client);
            if (userId == null || userId.isBlank()) {
                client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                return;
            }

            if (data == null || data.getMessageId() == null || data.getReaction() == null) {
                client.sendEvent(ERROR, Map.of("message", "지원하지 않는 리액션 타입입니다."));
                return;
            }

            /*
             * 기존 방식: 메시지 전체를 findById 후 add/remove 후 save
             *  - 매 요청당 읽기 + 쓰기 전체 문서 전송
             *  - 동시성 충돌 시 마지막 저장이 덮어쓰는 위험
             *
             * Message message = messageRepository.findById(data.getMessageId()).orElse(null);
             * ... mutate in memory ...
             * messageRepository.save(message);
             *
             * 개선: MongoDB findAndModify + $addToSet / $pull 로 원자적 업데이트
             *  - 한 번의 네트워크 라운드트립
             *  - 문서 일부 필드만 프로젝션 (room, reactions) 으로 전송량 감소
             *  - 동시성 안전하게 리액션 추가/제거
             */
            Message message = updateReactionsAtomic(data.getMessageId(), data.getReaction(), data.getType(), userId);
            if (message == null) {
                client.sendEvent(ERROR, Map.of("message", "메시지를 찾을 수 없습니다."));
                return;
            }

            log.debug("Message reaction processed - type: {}, reaction: {}, messageId: {}, userId: {}",
                data.getType(), data.getReaction(), message.getId(), userId);

            MessageReactionResponse response = new MessageReactionResponse(
                message.getId(),
                message.getReactions() != null ? message.getReactions() : Map.of()
            );

            socketIOServer.getRoomOperations(message.getRoomId())
                .sendEvent(MESSAGE_REACTION_UPDATE, response);

        } catch (Exception e) {
            log.error("Error handling messageReaction", e);
            client.sendEvent(ERROR, Map.of(
                "message", "리액션 처리 중 오류가 발생했습니다."
            ));
        }
    }
    
    private String getUserId(SocketIOClient client) {
        var user = (SocketUser) client.get("user");
        return user.id();
    }

    private Message updateReactionsAtomic(String messageId, String reaction, String type, String userId) {
        Query query = Query.query(Criteria.where("_id").is(messageId));
        // 필요한 필드만 반환하여 전송량 절약
        query.fields().include("room").include("reactions");

        Update update = new Update();
        switch (type) {
            case "add" -> update.addToSet("reactions." + reaction, userId);
            case "remove" -> update.pull("reactions." + reaction, userId);
            default -> {
                return null;
            }
        }

        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Message.class
        );
    }
}
