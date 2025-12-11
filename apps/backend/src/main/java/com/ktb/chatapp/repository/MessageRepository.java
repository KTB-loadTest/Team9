package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Message;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    // Page<Message> findByRoomIdAndIsDeletedAndTimestampBefore(String roomId, Boolean isDeleted, LocalDateTime timestamp, Pageable pageable);
    //메시지 페이지닝을 Page→Slice로 전환해 total count 쿼리를 없애고(채팅 스크롤에 불필요한 카운트 제거), 동일한 정렬/hasNext 계산만 유지
    Slice<Message> findByRoomIdAndIsDeletedAndTimestampBefore(
            String roomId,
            Boolean isDeleted,
            LocalDateTime timestamp,
            Pageable pageable
    );
    /**
     * 특정 시간 이후의 메시지 수 카운트 (삭제되지 않은 메시지만)
     * 최근 N분간 메시지 수를 조회할 때 사용
     */
    @Query(value = "{ 'room': ?0, 'isDeleted': false, 'timestamp': { $gte: ?1 } }", count = true)
    long countRecentMessagesByRoomId(String roomId, LocalDateTime since);

    /**
     * fileId로 메시지 조회 (파일 권한 검증용)
     */
    Optional<Message> findByFileId(String fileId);
}
