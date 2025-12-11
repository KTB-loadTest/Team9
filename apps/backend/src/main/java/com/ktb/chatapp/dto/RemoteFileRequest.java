package com.ktb.chatapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 프런트에서 S3 등에 업로드한 후 키/메타데이터를 전달하기 위한 요청 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteFileRequest {

    @Schema(description = "스토리지 키(또는 파일명)", example = "uploads/2024/12/abcd1234.png", required = true)
    private String key;

    @Schema(description = "접근 가능한 URL(서명 URL 등). 없으면 key만 저장.", example = "https://s3.amazonaws.com/bucket/uploads/abcd1234.png")
    private String url;

    @Schema(description = "원본 파일명", example = "profile.png", required = true)
    private String originalname;

    @Schema(description = "MIME 타입", example = "image/png", required = true)
    private String mimetype;

    @Schema(description = "파일 크기(byte)", example = "123456", required = true)
    private long size;
}
