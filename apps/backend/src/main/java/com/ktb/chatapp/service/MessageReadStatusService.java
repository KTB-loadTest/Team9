package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import com.mongodb.client.result.UpdateResult;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * 메시지 읽음 상태 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadStatusService {

    private final MongoTemplate mongoTemplate;

    /**
     * 메시지 읽음 상태 업데이트
     *
     * @param messageIds 읽음 상태를 업데이트할 메시지 리스트
     * @param userId 읽은 사용자 ID
     */
    public void updateReadStatus(List<String> messageIds, String userId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
//        메시지 읽음 업데이트를 개별 조회+save 루프 대신 MongoTemplate.updateMulti +
//        $addToSet로 일괄 처리해 네트워크 라운드트립/락 경합을 줄였습니다.
//        readers.userId 중복 시엔 필터로 제외해 불필요한 write도 피합니다.
        Message.MessageReader readerInfo = Message.MessageReader.builder()
                .userId(userId)
                .readAt(LocalDateTime.now())
                .build();
        
        try {
            Query query = Query.query(
                    Criteria.where("_id").in(messageIds)
                            .and("readers.userId").ne(userId)
            );

            Update update = new Update().addToSet("readers", readerInfo);

            UpdateResult result = mongoTemplate.updateMulti(query, update, Message.class);

            log.debug("Read status updated for {} messages by user {}",
                    result.getModifiedCount(), userId);

        } catch (Exception e) {
            log.error("Read status update error for user {}", userId, e);
        }
    }
}
