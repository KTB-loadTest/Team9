package com.ktb.chatapp.service;

import java.time.Duration;

public interface RateLimiter {

    RateLimitCheckResult checkRateLimit(String _clientId, int maxRequests, Duration window);
}
