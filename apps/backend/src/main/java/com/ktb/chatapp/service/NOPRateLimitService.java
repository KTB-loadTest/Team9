package com.ktb.chatapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "false")
public class NOPRateLimitService implements RateLimiter {

    @Override
    public RateLimitCheckResult checkRateLimit(String _clientId, int maxRequests, Duration window) {
        long windowSeconds = Math.max(1L, window.getSeconds());
        long resetEpochSeconds = Instant.now().plus(window).getEpochSecond();
        return RateLimitCheckResult.allowed(
                maxRequests, maxRequests, windowSeconds, resetEpochSeconds, windowSeconds);
    }
}
