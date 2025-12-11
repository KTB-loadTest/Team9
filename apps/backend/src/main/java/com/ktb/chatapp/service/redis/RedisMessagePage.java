package com.ktb.chatapp.service.redis;

import java.util.List;

public record RedisMessagePage(List<String> messages, boolean hasNext) {
}
