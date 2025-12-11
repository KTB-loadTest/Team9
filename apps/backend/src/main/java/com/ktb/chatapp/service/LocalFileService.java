package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RemoteFileRequest;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LocalFileService implements FileService {

    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final String defaultBaseUrl;

    public LocalFileService(@Value("${app.file.base-url:}") String defaultBaseUrl,
                            FileRepository fileRepository,
                            MessageRepository messageRepository,
                            RoomRepository roomRepository) {
        this.fileRepository = fileRepository;
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    @Override
    public FileUploadResult uploadFile(RemoteFileRequest request, String uploaderId) {
        File fileEntity = File.builder()
                .filename(request.getKey())
                .originalname(request.getOriginalname())
                .mimetype(request.getMimetype())
                .size(request.getSize())
                .path(resolvePath(request))
                .user(uploaderId)
                .uploadDate(LocalDateTime.now())
                .build();

        File savedFile = fileRepository.save(fileEntity);

        return FileUploadResult.builder()
                .success(true)
                .file(savedFile)
                .build();
    }

    @Override
    public String storeFile(RemoteFileRequest request) {
        return resolvePath(request);
    }

    @Override
    public Resource loadFileAsResource(String fileName, String requesterId) {
        // 1. 파일 조회
        File fileEntity = fileRepository.findByFilename(fileName)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다: " + fileName));

        // 2. 메시지 조회 (파일과 메시지 연결 확인) - 효율적인 쿼리 메서드 사용
        Message message = messageRepository.findByFileId(fileEntity.getId())
                .orElseThrow(() -> new RuntimeException("파일과 연결된 메시지를 찾을 수 없습니다"));

        // 3. 방 조회 (사용자가 방 참가자인지 확인)
        Room room = roomRepository.findById(message.getRoomId())
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다"));

        // 4. 권한 검증
        if (!room.getParticipantIds().contains(requesterId)) {
            log.warn("파일 접근 권한 없음: {} (사용자: {})", fileName, requesterId);
            throw new RuntimeException("파일에 접근할 권한이 없습니다");
        }

        // 5. 파일 경로(URL 또는 키)로 리소스 생성
        String targetPath = fileEntity.getPath() != null ? fileEntity.getPath() : fileEntity.getFilename();
        Resource resource = toUrlResource(targetPath);
        if (resource.exists()) {
            log.info("파일 로드 성공: {} (사용자: {})", fileName, requesterId);
            return resource;
        } else {
            throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
        }
    }

    @Override
    public boolean deleteFile(String fileId, String requesterId) {
        try {
            File fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

            // 삭제 권한 검증 (업로더만 삭제 가능)
            if (!fileEntity.getUser().equals(requesterId)) {
                throw new RuntimeException("파일을 삭제할 권한이 없습니다.");
            }

            // 데이터베이스에서 제거
            fileRepository.delete(fileEntity);

            log.info("파일 메타데이터 삭제 완료: {} (사용자: {})", fileId, requesterId);
            return true;

        } catch (Exception e) {
            log.error("파일 삭제 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.", e);
        }
    }

    private String resolvePath(RemoteFileRequest request) {
        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            return request.getUrl();
        }
        // URL이 없으면 키를 그대로 반환하거나 base-url이 있으면 prefix
        if (defaultBaseUrl != null && !defaultBaseUrl.isBlank()) {
            return defaultBaseUrl.endsWith("/")
                    ? defaultBaseUrl + request.getKey()
                    : defaultBaseUrl + "/" + request.getKey();
        }
        return request.getKey();
    }

    private Resource toUrlResource(String path) {
        try {
            // 절대 URL이면 그대로, 아니면 file: 로 시도
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return new UrlResource(new URL(path));
            }
            return new UrlResource("file:" + path);
        } catch (MalformedURLException e) {
            throw new RuntimeException("잘못된 파일 경로/URL입니다: " + path, e);
        }
    }
}
