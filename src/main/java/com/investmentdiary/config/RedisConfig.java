package com.investmentdiary.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis 설정
 * REDIS_HOST 환경 변수가 설정되어 있고 비어있지 않을 때만 Redis 자동 구성을 활성화합니다.
 * REDIS_HOST가 비어있거나 설정되지 않으면 Redis 자동 구성을 비활성화하여
 * 메모리 Map fallback을 사용합니다.
 */
@Configuration
@ConditionalOnExpression("'${spring.data.redis.host:}' != ''")
@Import(RedisAutoConfiguration.class)
public class RedisConfig {
    // REDIS_HOST가 설정되어 있고 비어있지 않으면 Redis 자동 구성을 활성화합니다.
    // REDIS_HOST가 비어있거나 설정되지 않으면 이 설정이 활성화되지 않아
    // ChallengeStorageService가 메모리 Map을 사용합니다.
}

