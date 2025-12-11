package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RemoteFileRequest;
import org.springframework.core.io.Resource;

public interface FileService {

    FileUploadResult uploadFile(RemoteFileRequest request, String uploaderId);

    String storeFile(RemoteFileRequest request);

    Resource loadFileAsResource(String fileName, String requesterId);

    boolean deleteFile(String fileId, String requesterId);
}
