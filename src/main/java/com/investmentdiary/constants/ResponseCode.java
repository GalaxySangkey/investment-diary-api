package com.investmentdiary.constants;

import java.util.HashMap;
import java.util.Map;

/**
 * API 응답 코드 상수 정의
 * 텍스트 코드와 숫자 코드를 모두 제공하여 일관성과 호환성 확보
 */
public class ResponseCode {
    
    // ==================== 성공 코드 ====================
    
    /**
     * 일반적인 성공
     */
    public static final String SUCCESS = "SUCCESS";
    public static final int SUCCESS_CODE = 200;
    
    /**
     * 생성 성공
     */
    public static final String CREATED = "CREATED";
    public static final int CREATED_CODE = 201;
    
    /**
     * 수정 성공
     */
    public static final String UPDATED = "UPDATED";
    public static final int UPDATED_CODE = 202;
    
    /**
     * 삭제 성공
     */
    public static final String DELETED = "DELETED";
    public static final int DELETED_CODE = 203;
    
    // ==================== 인증/인가 관련 에러 ====================
    
    /**
     * 인증 실패
     */
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final int AUTHENTICATION_FAILED_CODE = 401;
    
    /**
     * 권한 없음
     */
    public static final String AUTHORIZATION_FAILED = "AUTHORIZATION_FAILED";
    public static final int AUTHORIZATION_FAILED_CODE = 403;
    
    /**
     * 토큰 만료
     */
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final int TOKEN_EXPIRED_CODE = 401;
    
    /**
     * 토큰 무효
     */
    public static final String TOKEN_INVALID = "TOKEN_INVALID";
    public static final int TOKEN_INVALID_CODE = 401;
    
    // ==================== 회원가입/로그인 관련 에러 ====================
    
    /**
     * 회원가입 실패
     */
    public static final String REGISTER_FAILED = "REGISTER_FAILED";
    public static final int REGISTER_FAILED_CODE = 400;
    
    /**
     * 로그인 실패
     */
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final int LOGIN_FAILED_CODE = 401;
    
    /**
     * 중복 사용자계정
     */
    public static final String DUPLICATE_USERNAME = "DUPLICATE_USERNAME";
    public static final int DUPLICATE_USERNAME_CODE = 400;
    
    /**
     * 중복 이메일
     */
    public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final int DUPLICATE_EMAIL_CODE = 400;
    
    /**
     * 잘못된 자격 증명
     */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final int INVALID_CREDENTIALS_CODE = 401;
    
    /**
     * 계정 잠김
     */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final int ACCOUNT_LOCKED_CODE = 423;
    
    // ==================== 유효성 검사 관련 에러 ====================
    
    /**
     * 유효성 검사 실패
     */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final int VALIDATION_FAILED_CODE = 400;
    
    /**
     * 필수 필드 누락
     */
    public static final String REQUIRED_FIELD_MISSING = "REQUIRED_FIELD_MISSING";
    public static final int REQUIRED_FIELD_MISSING_CODE = 400;
    
    /**
     * 잘못된 형식
     */
    public static final String INVALID_FORMAT = "INVALID_FORMAT";
    public static final int INVALID_FORMAT_CODE = 400;
    
    // ==================== 리소스 관련 에러 ====================
    
    /**
     * 사용자 찾을 수 없음
     */
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final int USER_NOT_FOUND_CODE = 404;
    
    /**
     * 투자 기록 찾을 수 없음
     */
    public static final String INVESTMENT_NOT_FOUND = "INVESTMENT_NOT_FOUND";
    public static final int INVESTMENT_NOT_FOUND_CODE = 404;
    
    /**
     * 포트폴리오 찾을 수 없음
     */
    public static final String PORTFOLIO_NOT_FOUND = "PORTFOLIO_NOT_FOUND";
    public static final int PORTFOLIO_NOT_FOUND_CODE = 404;
    
    /**
     * 주식 데이터 찾을 수 없음
     */
    public static final String STOCK_DATA_NOT_FOUND = "STOCK_DATA_NOT_FOUND";
    public static final int STOCK_DATA_NOT_FOUND_CODE = 404;
    
    /**
     * 커뮤니티 게시글 찾을 수 없음
     */
    public static final String POST_NOT_FOUND = "POST_NOT_FOUND";
    public static final int POST_NOT_FOUND_CODE = 404;
    
    /**
     * 댓글 찾을 수 없음
     */
    public static final String COMMENT_NOT_FOUND = "COMMENT_NOT_FOUND";
    public static final int COMMENT_NOT_FOUND_CODE = 404;
    
    // ==================== 비즈니스 로직 관련 에러 ====================
    
    /**
     * 중복 데이터
     */
    public static final String DUPLICATE_DATA = "DUPLICATE_DATA";
    public static final int DUPLICATE_DATA_CODE = 409;
    
    /**
     * 데이터 충돌
     */
    public static final String DATA_CONFLICT = "DATA_CONFLICT";
    public static final int DATA_CONFLICT_CODE = 409;
    
    /**
     * 비즈니스 규칙 위반
     */
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    public static final int BUSINESS_RULE_VIOLATION_CODE = 422;
    
    /**
     * 허용되지 않는 작업
     */
    public static final String OPERATION_NOT_ALLOWED = "OPERATION_NOT_ALLOWED";
    public static final int OPERATION_NOT_ALLOWED_CODE = 422;
    
    // ==================== 서버 관련 에러 ====================
    
    /**
     * 서버 내부 오류
     */
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final int INTERNAL_SERVER_ERROR_CODE = 500;
    
    /**
     * 서비스 사용 불가
     */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    public static final int SERVICE_UNAVAILABLE_CODE = 503;
    
    /**
     * 데이터베이스 오류
     */
    public static final String DATABASE_ERROR = "DATABASE_ERROR";
    public static final int DATABASE_ERROR_CODE = 500;
    
    /**
     * 외부 API 오류
     */
    public static final String EXTERNAL_API_ERROR = "EXTERNAL_API_ERROR";
    public static final int EXTERNAL_API_ERROR_CODE = 502;
    
    // ==================== 요청 관련 에러 ====================
    
    /**
     * 잘못된 요청
     */
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final int BAD_REQUEST_CODE = 400;
    
    /**
     * 요청 제한 초과
     */
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final int RATE_LIMIT_EXCEEDED_CODE = 429;
    
    /**
     * 지원하지 않는 미디어 타입
     */
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final int UNSUPPORTED_MEDIA_TYPE_CODE = 415;
    
    /**
     * 요청 크기 초과
     */
    public static final String REQUEST_TOO_LARGE = "REQUEST_TOO_LARGE";
    public static final int REQUEST_TOO_LARGE_CODE = 413;
    
    // ==================== 매핑 Map들 ====================
    
    /**
     * 텍스트 코드 -> 숫자 코드 매핑
     */
    private static final Map<String, Integer> TEXT_TO_NUMBER = new HashMap<>();
    
    /**
     * 숫자 코드 -> 텍스트 코드 매핑
     */
    private static final Map<Integer, String> NUMBER_TO_TEXT = new HashMap<>();
    
    static {
        // 성공 코드
        TEXT_TO_NUMBER.put(SUCCESS, SUCCESS_CODE);
        TEXT_TO_NUMBER.put(CREATED, CREATED_CODE);
        TEXT_TO_NUMBER.put(UPDATED, UPDATED_CODE);
        TEXT_TO_NUMBER.put(DELETED, DELETED_CODE);
        
        // 인증/인가 관련 에러
        TEXT_TO_NUMBER.put(AUTHENTICATION_FAILED, AUTHENTICATION_FAILED_CODE);
        TEXT_TO_NUMBER.put(AUTHORIZATION_FAILED, AUTHORIZATION_FAILED_CODE);
        TEXT_TO_NUMBER.put(TOKEN_EXPIRED, TOKEN_EXPIRED_CODE);
        TEXT_TO_NUMBER.put(TOKEN_INVALID, TOKEN_INVALID_CODE);
        
        // 회원가입/로그인 관련 에러
        TEXT_TO_NUMBER.put(REGISTER_FAILED, REGISTER_FAILED_CODE);
        TEXT_TO_NUMBER.put(LOGIN_FAILED, LOGIN_FAILED_CODE);
        TEXT_TO_NUMBER.put(DUPLICATE_USERNAME, DUPLICATE_USERNAME_CODE);
        TEXT_TO_NUMBER.put(DUPLICATE_EMAIL, DUPLICATE_EMAIL_CODE);
        TEXT_TO_NUMBER.put(INVALID_CREDENTIALS, INVALID_CREDENTIALS_CODE);
        TEXT_TO_NUMBER.put(ACCOUNT_LOCKED, ACCOUNT_LOCKED_CODE);
        
        // 유효성 검사 관련 에러
        TEXT_TO_NUMBER.put(VALIDATION_FAILED, VALIDATION_FAILED_CODE);
        TEXT_TO_NUMBER.put(REQUIRED_FIELD_MISSING, REQUIRED_FIELD_MISSING_CODE);
        TEXT_TO_NUMBER.put(INVALID_FORMAT, INVALID_FORMAT_CODE);
        
        // 리소스 관련 에러
        TEXT_TO_NUMBER.put(USER_NOT_FOUND, USER_NOT_FOUND_CODE);
        TEXT_TO_NUMBER.put(INVESTMENT_NOT_FOUND, INVESTMENT_NOT_FOUND_CODE);
        TEXT_TO_NUMBER.put(PORTFOLIO_NOT_FOUND, PORTFOLIO_NOT_FOUND_CODE);
        TEXT_TO_NUMBER.put(STOCK_DATA_NOT_FOUND, STOCK_DATA_NOT_FOUND_CODE);
        TEXT_TO_NUMBER.put(POST_NOT_FOUND, POST_NOT_FOUND_CODE);
        TEXT_TO_NUMBER.put(COMMENT_NOT_FOUND, COMMENT_NOT_FOUND_CODE);
        
        // 비즈니스 로직 관련 에러
        TEXT_TO_NUMBER.put(DUPLICATE_DATA, DUPLICATE_DATA_CODE);
        TEXT_TO_NUMBER.put(DATA_CONFLICT, DATA_CONFLICT_CODE);
        TEXT_TO_NUMBER.put(BUSINESS_RULE_VIOLATION, BUSINESS_RULE_VIOLATION_CODE);
        TEXT_TO_NUMBER.put(OPERATION_NOT_ALLOWED, OPERATION_NOT_ALLOWED_CODE);
        
        // 서버 관련 에러
        TEXT_TO_NUMBER.put(INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_CODE);
        TEXT_TO_NUMBER.put(SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_CODE);
        TEXT_TO_NUMBER.put(DATABASE_ERROR, DATABASE_ERROR_CODE);
        TEXT_TO_NUMBER.put(EXTERNAL_API_ERROR, EXTERNAL_API_ERROR_CODE);
        
        // 요청 관련 에러
        TEXT_TO_NUMBER.put(BAD_REQUEST, BAD_REQUEST_CODE);
        TEXT_TO_NUMBER.put(RATE_LIMIT_EXCEEDED, RATE_LIMIT_EXCEEDED_CODE);
        TEXT_TO_NUMBER.put(UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE_CODE);
        TEXT_TO_NUMBER.put(REQUEST_TOO_LARGE, REQUEST_TOO_LARGE_CODE);
        
        // 역방향 매핑 생성
        for (Map.Entry<String, Integer> entry : TEXT_TO_NUMBER.entrySet()) {
            // 중복된 숫자 코드가 있을 경우 첫 번째 것만 저장
            if (!NUMBER_TO_TEXT.containsKey(entry.getValue())) {
                NUMBER_TO_TEXT.put(entry.getValue(), entry.getKey());
            }
        }
    }
    
    // ==================== 유틸리티 메서드 ====================
    
    /**
     * 텍스트 코드로 숫자 코드 조회
     */
    public static int getNumericCode(String textCode) {
        return TEXT_TO_NUMBER.getOrDefault(textCode, INTERNAL_SERVER_ERROR_CODE);
    }
    
    /**
     * 숫자 코드로 텍스트 코드 조회
     */
    public static String getTextCode(int numericCode) {
        return NUMBER_TO_TEXT.getOrDefault(numericCode, INTERNAL_SERVER_ERROR);
    }
}