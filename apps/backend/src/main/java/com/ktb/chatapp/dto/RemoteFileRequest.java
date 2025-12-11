package com.ktb.chatapp.dto;

import lombok.Getter;

@Getter
public class RemoteFileRequest {
    private String url;

    private String key;

    private long size;

    private String mimetype;

    private String originalname;

}
