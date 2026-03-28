package com.investmentdiary.event;

/**
 * 회원가입 트랜잭션이 커밋된 뒤 후처리(기본 포트폴리오 생성 등)에 사용한다.
 */
public record UserRegisteredEvent(Long userId) {}
