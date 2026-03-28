package com.investmentdiary.service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Challenge 저장 서비스
 * Redis를 기본으로 사용하며, 환경 변수로 Redis 접속 정보를 받습니다.
 * Redis 연결 실패 시에만 메모리 Map으로 fallback합니다.
 * 
 * 필수 환경 변수:
 * - REDIS_HOST: Redis 호스트 주소
 * - REDIS_PORT: Redis 포트 (기본값: 6379)
 * - REDIS_PASSWORD: Redis 비밀번호 (선택)
 */
@Service
public class ChallengeStorageService {
    
    private static final Logger log = LoggerFactory.getLogger(ChallengeStorageService.class);
    private static final String REDIS_KEY_PREFIX = "webauthn:challenge:";
    
    @Value("${webauthn.challenge.ttl:300}") // 기본 5분
    private long challengeTtlSeconds;
    
    @Value("${spring.data.redis.host:}") // Spring 설정에서 Redis 호스트 읽기
    private String redisHost;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Redis Template (환경 변수로 설정된 Redis 사용)
    private RedisTemplate<String, String> redisTemplate;
    
    // Redis 연결 실패 시에만 사용할 메모리 저장소 (fallback)
    private final Map<String, ChallengeData> memoryStore = new ConcurrentHashMap<>();
    private boolean useMemoryFallback = false;
    
    @Autowired(required = false)
    public void setRedisTemplate(RedisTemplate<String, String> redisTemplate) {
        if (redisTemplate != null) {
            this.redisTemplate = redisTemplate;
            log.info("RedisTemplate 주입 완료: host={}", redisHost != null && !redisHost.isEmpty() ? redisHost : "localhost");
        }
    }
    
    @PostConstruct
    public void init() {
        if (redisTemplate != null && redisHost != null && !redisHost.isEmpty()) {
            // Redis 연결 테스트
            try {
                redisTemplate.opsForValue().set("webauthn:test:connection", "test", java.time.Duration.ofSeconds(1));
                redisTemplate.delete("webauthn:test:connection");
                log.info("Challenge 저장소: Redis 사용 (host={}, TTL: {}초)", redisHost, challengeTtlSeconds);
                useMemoryFallback = false;
            } catch (Exception e) {
                log.warn("Redis 연결 실패, 메모리 Map으로 fallback: {}", e.getMessage());
                useMemoryFallback = true;
            }
        } else {
            log.warn("Redis가 설정되지 않았습니다. REDIS_HOST 환경 변수를 설정하세요. 메모리 Map을 사용합니다.");
            log.warn("주의: 메모리 Map은 서버 재시작 시 데이터가 초기화되며, 여러 서버 환경에서는 사용할 수 없습니다.");
            useMemoryFallback = true;
        }
    }
    
    /**
     * Challenge 저장
     */
    public void saveChallenge(String challenge, ChallengeData data) {
        try {
            if (!useMemoryFallback && redisTemplate != null) {
                // Redis 사용
                String key = REDIS_KEY_PREFIX + challenge;
                String value = objectMapper.writeValueAsString(data);
                redisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(challengeTtlSeconds));
                log.debug("Challenge 저장 (Redis): {}", challenge);
            } else {
                // 메모리 Map 사용 (fallback)
                memoryStore.put(challenge, data);
                log.debug("Challenge 저장 (메모리 fallback): {}", challenge);
            }
        } catch (Exception e) {
            log.error("Challenge 저장 실패: {}", e.getMessage(), e);
            // Redis 실패 시 메모리로 fallback
            if (!useMemoryFallback) {
                useMemoryFallback = true;
                log.warn("Redis 저장 실패, 메모리로 fallback 전환: {}", challenge);
            }
            memoryStore.put(challenge, data);
        }
    }
    
    /**
     * Challenge 조회
     */
    public ChallengeData getChallenge(String challenge) {
        try {
            if (!useMemoryFallback && redisTemplate != null) {
                // Redis에서 조회
                String key = REDIS_KEY_PREFIX + challenge;
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    ChallengeData data = objectMapper.readValue(value, ChallengeData.class);
                    log.debug("Challenge 조회 (Redis): {}", challenge);
                    return data;
                }
                return null;
            } else {
                // 메모리 Map에서 조회 (fallback)
                ChallengeData data = memoryStore.get(challenge);
                if (data != null) {
                    log.debug("Challenge 조회 (메모리 fallback): {}", challenge);
                    return data;
                }
                return null;
            }
        } catch (Exception e) {
            log.error("Challenge 조회 실패: {}", e.getMessage(), e);
            // Redis 실패 시 메모리에서 조회
            if (!useMemoryFallback) {
                useMemoryFallback = true;
                log.warn("Redis 조회 실패, 메모리로 fallback 전환: {}", challenge);
            }
            return memoryStore.get(challenge);
        }
    }
    
    /**
     * Challenge 삭제
     */
    public void deleteChallenge(String challenge) {
        try {
            if (!useMemoryFallback && redisTemplate != null) {
                // Redis에서 삭제
                String key = REDIS_KEY_PREFIX + challenge;
                redisTemplate.delete(key);
                log.debug("Challenge 삭제 (Redis): {}", challenge);
            } else {
                // 메모리 Map에서 삭제 (fallback)
                memoryStore.remove(challenge);
                log.debug("Challenge 삭제 (메모리 fallback): {}", challenge);
            }
        } catch (Exception e) {
            log.error("Challenge 삭제 실패: {}", e.getMessage(), e);
            // Redis 실패 시 메모리에서 삭제
            if (!useMemoryFallback) {
                useMemoryFallback = true;
                log.warn("Redis 삭제 실패, 메모리로 fallback 전환: {}", challenge);
            }
            memoryStore.remove(challenge);
        }
    }
    
    /**
     * Challenge 데이터 클래스
     */
    public static class ChallengeData {
        public Long userId;
        public String username;
        public String email;
        public String name; // 실제 이름
        public String nickname;
        public String phone;
        public String password; // 비밀번호 (선택적)
        public String deviceName;
        public LocalDateTime createdAt;
        
        // Jackson 직렬화를 위한 기본 생성자
        public ChallengeData() {}
    }
}

