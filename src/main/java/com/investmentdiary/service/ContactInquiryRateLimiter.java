package com.investmentdiary.service;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.investmentdiary.exception.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 문의하기 API 남용 방지: IP(또는 X-Forwarded-For 첫 주소) 기준 슬라이딩 윈도우.
 * 단일 인스턴스 기준; 수평 확장 시 Redis 등 분산 카운터로 교체 권장.
 */
@Component
@Slf4j
public class ContactInquiryRateLimiter {

    private final boolean enabled;
    private final int maxRequestsPerWindow;
    private final long windowMillis;

    private final ConcurrentHashMap<String, ArrayDeque<Long>> hitsByKey = new ConcurrentHashMap<>();

    public ContactInquiryRateLimiter(
            @Value("${contact.inquiry.rate-limit.enabled:true}") boolean enabled,
            @Value("${contact.inquiry.rate-limit.max-requests-per-window:5}") int maxRequestsPerWindow,
            @Value("${contact.inquiry.rate-limit.window-minutes:15}") int windowMinutes) {
        this.enabled = enabled;
        this.maxRequestsPerWindow = Math.max(1, maxRequestsPerWindow);
        this.windowMillis = Math.max(60_000L, windowMinutes * 60_000L);
    }

    /**
     * 허용되면 기록하고 반환. 초과 시 {@link RateLimitExceededException}.
     */
    public void checkAndRecord(HttpServletRequest request) {
        if (!enabled) {
            return;
        }
        String key = resolveClientKey(request);
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        ArrayDeque<Long> q = hitsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) {
                q.pollFirst();
            }
            if (q.size() >= maxRequestsPerWindow) {
                long oldest = q.peekFirst() != null ? q.peekFirst() : now;
                long retryAfterSec = Math.max(1, (oldest + windowMillis - now + 999) / 1000);
                log.warn("문의하기 레이트 리밋 초과: key={}, windowMin={}, max={}, retryAfterSec={}",
                        key, windowMillis / 60_000, maxRequestsPerWindow, retryAfterSec);
                throw new RateLimitExceededException(
                        "짧은 시간에 너무 많은 문의를 보냈습니다. 잠시 후 다시 시도해 주세요.",
                        retryAfterSec);
            }
            q.addLast(now);
        }
    }

    static String resolveClientKey(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return "ip:" + first;
            }
        }
        String addr = request.getRemoteAddr();
        return addr != null ? "ip:" + addr : "ip:unknown";
    }
}
