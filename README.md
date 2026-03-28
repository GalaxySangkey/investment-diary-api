# 🚀 투자일지 API 서버

투자일지 웹/앱을 위한 완전한 Spring Boot REST API 서버입니다.

**관련 저장소:** 웹 프론트엔드는 별도 GitHub 저장소로 공개하는 경우가 많습니다. 공개 후 아래 URL을 실제 프론트 저장소 주소로 바꿔 주세요.

- 프론트엔드(예시): `https://github.com/<사용자명>/<프론트-저장소명>`

## ✨ 주요 기능

### 🔐 **인증 및 보안**
- **WebAuthn 기반 비밀번호 없는 인증** - 최신 표준 인증 방식
- JWT 기반 사용자 인증 (Access Token + Refresh Token)
- Spring Security를 통한 역할 기반 접근 제어
- AES-256 암호화로 민감 정보 보호
- bcrypt 해시로 비밀번호 보안 (레거시 지원)

### 💰 **투자 관리**
- 매수/매도 기록 CRUD
- **자산 유형 지원**: 주식(STOCK), 외환(CURRENCY)
- **외환거래 기록**: 통화쌍, 기준통화, 상대통화, 환율 관리
- 투자 기록 수정/삭제
- 포트폴리오 요약 및 분석
- 실현/미실현 손익 계산
- 투자 패턴 분석
- 매도 시 보유 종목/통화만 선택 가능 (완전 매도된 자산 제외)
- **외환거래 계산**: 수량 × 환율 = 총 투자금액 (원화)

### 📅 **캘린더 기능**
- 월별/주별 투자 요약
- 일별 수익률 표시
- **전날 대비 평단가 기준 수익률 변화량 계산**: 각 날짜에 보유 종목의 전날 대비 수익률 변화량을 가중 평균으로 계산
  - 전날 종가와 오늘 종가를 비교하여 평단가 기준 수익률 변화량 계산
  - 보유 수량과 평단가를 가중치로 사용한 가중 평균 계산
  - 투자 기록이 없는 날짜에도 보유 종목이 있으면 자동 계산
- **일별 보유 종목 조회 API**: 특정 날짜의 보유 종목 목록과 상세 정보 조회
  - 각 종목별 전날 대비 수익률 변화량, 최종 수익률, 수익금 계산
  - 환율 적용하여 사용자 통화로 변환
- 투자 패턴 시각화
- 날짜별 투자 기록 조회

### 💼 **자산관리**
- 고정 수입/지출 관리 (CRUD)
- 고정 수입/지출 기간 설정 (시작일/종료일)
- 자산 설정 관리 (시작 날짜, 시작 금액, 저축액, 투자시드 등)
- 월별 실제 금액 입력 및 차이 계산
- 기간별 자산 변화 계산
- 순수 현금 잔액 계산

### 👥 **커뮤니티**
- 투자 관련 게시글 작성/조회
- 댓글 시스템
- 사용자 레벨링 및 포인트

### 📬 **메시지 큐 시스템**
- API 요청을 큐로 처리하여 서버 장애 시 요청 누락 방지
- DB 우선 저장으로 데이터 보존 보장
- 자동 복구 및 재시도 메커니즘
- 비동기 처리로 성능 향상

### 📊 **분석 및 통계**
- 포트폴리오 다각화 분석
- 리스크 점수 계산
- 리밸런싱 필요성 체크

### 📈 **주식 데이터 자동 업데이트**
- **일일 종가 업데이트**: 매일 오전 9시에 보유 종목의 전날 종가 자동 조회 및 저장
- **월별 배당 정보 업데이트**: 매월 1일 오전 10시에 보유 종목의 배당 정보 자동 조회 및 저장
- **일일 환율 업데이트**: 매일 오전 9시 30분에 주요 통화쌍 환율 자동 조회 및 저장
- **과거 종가 데이터 채우기**: 
  - 매수 기록 생성 시 해당 매수일부터 오늘까지 종가 데이터 자동 조회
  - 수동으로 특정 종목 또는 모든 보유 종목의 과거 종가 데이터 채우기 가능
  - 종가 정보는 종목별 공유 데이터로 효율적 관리

## 🛠 기술 스택

### **Backend Framework**
- **Spring Boot 3.x** - 최신 Spring Boot 버전
- **Java 17** - LTS Java 버전
- **Spring Security 6.x** - 보안 프레임워크
- **Spring Data JPA** - 데이터 접근 계층

### **Database & Cache**
- **MariaDB 10.6+** - 메인 데이터베이스 (MySQL 호환)
- **Redis** - 세션, 캐시, WebAuthn Challenge 및 메시지 큐 저장소 (환경 변수로 선택적 사용)
- **RabbitMQ** - 메시지 큐 브로커 (환경 변수로 선택적 사용)
- **Flyway** - 데이터베이스 마이그레이션

### **Security & Authentication**
- **WebAuthn (webauthn4j)** - 비밀번호 없는 인증 (FIDO2/WebAuthn 표준)
- **JWT (JSON Web Tokens)** - 토큰 기반 인증
- **AES-256** - 민감 데이터 암호화
- **bcrypt** - 비밀번호 해싱 (레거시 지원)
- **Spring Security** - 보안 프레임워크

### **API Documentation**
- **Swagger/OpenAPI 3** - API 문서화
- **SpringDoc** - Swagger 자동 생성

### **Utilities**
- **Lombok** - 보일러플레이트 코드 제거
- **Bean Validation** - 입력값 검증
- **SLF4J + Logback** - 로깅

## 🏗 프로젝트 구조

```
investment-diary-api/
├── src/main/java/com/investmentdiary/
│   ├── config/                          # 설정 클래스들
│   │   ├── SecurityConfig.java         # Spring Security 설정
│   │   ├── QueueConfig.java            # 메시지 큐 설정 (RabbitMQ/Redis)
│   │   └── SwaggerConfig.java          # Swagger 설정
│   │
│   ├── controller/                      # REST API 컨트롤러
│   │   ├── AuthController.java         # 인증 관련 API
│   │   ├── QueueController.java        # 메시지 큐 API (요청 제출/상태 조회)
│   │   ├── InvestmentController.java   # 투자 기록 API
│   │   ├── PortfolioController.java    # 포트폴리오 API
│   │   ├── CalendarController.java     # 캘린더 API
│   │   └── AssetController.java        # 자산관리 API
│   │
│   ├── service/                         # 비즈니스 로직 서비스
│   │   ├── AuthService.java            # 인증 서비스 (레거시 패스워드 기반)
│   │   ├── WebAuthnService.java        # WebAuthn 인증 서비스
│   │   ├── ChallengeStorageService.java # WebAuthn Challenge 저장 서비스
│   │   ├── QueueService.java           # 메시지 큐 서비스 (요청 저장/조회)
│   │   ├── MessageQueueProducer.java   # 메시지 큐 Producer (RabbitMQ/Redis/Memory)
│   │   ├── QueueConsumer.java         # 메시지 큐 Consumer (백그라운드 처리)
│   │   ├── QueueProcessor.java        # 큐 요청 처리기 (비즈니스 로직 실행)
│   │   ├── InvestmentService.java      # 투자 기록 서비스
│   │   ├── PortfolioService.java       # 포트폴리오 서비스
│   │   ├── CalendarService.java        # 캘린더 서비스
│   │   ├── AssetService.java           # 자산관리 서비스
│   │   ├── StockDataBatchService.java  # 주식 데이터 배치 서비스 (종가, 배당, 환율)
│   │   └── YahooFinanceService.java    # Python 서비스 연동 서비스
│   │
│   ├── repository/                      # 데이터 접근 계층
│   │   ├── UserRepository.java         # 사용자 레포지토리
│   │   ├── WebAuthnCredentialRepository.java # WebAuthn 인증기 레포지토리
│   │   ├── ApiRequestQueueRepository.java # API 요청 큐 레포지토리
│   │   ├── InvestmentRecordRepository.java # 투자 기록 레포지토리
│   │   ├── UserSessionRepository.java  # 사용자 세션 레포지토리
│   │   ├── PortfolioSettingsRepository.java # 포트폴리오 설정 레포지토리
│   │   ├── AssetSettingsRepository.java # 자산 설정 레포지토리
│   │   ├── MonthlyActualBalanceRepository.java # 월별 실제 금액 레포지토리
│   │   ├── FixedIncomeRepository.java  # 고정 수입 레포지토리
│   │   ├── FixedExpenseRepository.java # 고정 지출 레포지토리
│   │   ├── StockPriceRepository.java   # 주식 종가 레포지토리
│   │   ├── StockDividendRepository.java # 주식 배당 레포지토리
│   │   ├── ExchangeRateRepository.java # 환율 레포지토리
│   │   └── StockTickerMappingRepository.java # 종목코드-티커 매핑 레포지토리
│   │
│   ├── entity/                          # JPA 엔티티
│   │   ├── User.java                   # 사용자 엔티티 (password nullable)
│   │   ├── WebAuthnCredential.java     # WebAuthn 인증기 정보 엔티티
│   │   ├── ApiRequestQueue.java       # API 요청 큐 엔티티
│   │   ├── InvestmentRecord.java       # 투자 기록 엔티티
│   │   ├── PortfolioSettings.java      # 포트폴리오 설정 엔티티
│   │   ├── UserSession.java            # 사용자 세션 엔티티
│   │   ├── AssetSettings.java          # 자산 설정 엔티티
│   │   ├── MonthlyActualBalance.java   # 월별 실제 금액 엔티티
│   │   ├── FixedIncome.java            # 고정 수입 엔티티
│   │   ├── FixedExpense.java           # 고정 지출 엔티티
│   │   ├── StockPrice.java             # 주식 종가 엔티티
│   │   ├── StockDividend.java          # 주식 배당 엔티티
│   │   ├── ExchangeRate.java           # 환율 엔티티
│   │   ├── StockTickerMapping.java     # 종목코드-티커 매핑 엔티티
│   │   ├── CommunityPost.java          # 커뮤니티 게시글 엔티티
│   │   ├── CommunityComment.java       # 커뮤니티 댓글 엔티티
│   │   ├── UserLevel.java              # 사용자 레벨 엔티티
│   │   ├── PointHistory.java           # 포인트 히스토리 엔티티
│   │   └── Notification.java           # 알림 엔티티
│   │
│   ├── dto/                             # 데이터 전송 객체
│   │   ├── ApiResponse.java            # 공통 API 응답 형식 (레거시)
│   │   ├── UnifiedApiResponse.java     # 통일된 API 응답 형식 (성공/실패 일관성)
│   │   ├── auth/                       # 인증 관련 DTO
│   │   │   ├── WebAuthnRegisterStartRequest.java
│   │   │   ├── WebAuthnRegisterStartResponse.java
│   │   │   ├── WebAuthnRegisterFinishRequest.java
│   │   │   ├── WebAuthnLoginStartRequest.java
│   │   │   ├── WebAuthnLoginStartResponse.java
│   │   │   └── WebAuthnLoginFinishRequest.java
│   │   ├── queue/                      # 메시지 큐 관련 DTO
│   │   │   ├── QueueSubmitRequest.java
│   │   │   ├── QueueSubmitResponse.java
│   │   │   └── QueueStatusResponse.java
│   │   ├── investment/                 # 투자 관련 DTO
│   │   └── portfolio/                  # 포트폴리오 관련 DTO
│   │
│   ├── security/                        # 보안 관련 클래스
│   │   ├── JwtTokenProvider.java       # JWT 토큰 생성/검증
│   │   ├── JwtAuthenticationFilter.java # JWT 인증 필터
│   │   ├── JwtAuthenticationEntryPoint.java # 인증 실패 처리
│   │   └── CustomUserDetailsService.java # 사용자 인증 정보 로드
│   │
│   ├── exception/                       # 예외 처리
│   │   ├── GlobalExceptionHandler.java # 전역 예외 처리기
│   │   ├── AuthenticationException.java # 인증 예외
│   │   ├── UserNotFoundException.java   # 사용자 찾을 수 없음 예외
│   │   ├── InvestmentNotFoundException.java # 투자 기록 찾을 수 없음 예외
│   │   └── PortfolioNotFoundException.java # 포트폴리오 찾을 수 없음 예외
│   │
│   ├── util/                            # 유틸리티 클래스
│   │   └── EncryptionUtil.java         # 암호화 유틸리티
│   │
│   └── InvestmentDiaryApplication.java  # 메인 애플리케이션 클래스
│
├── src/main/resources/
│   ├── application.yml                  # 애플리케이션 설정
│   └── database/                        # 데이터베이스 스크립트
│       ├── schema.sql                   # 데이터베이스 스키마
│       ├── security_config.sql          # 보안 설정
│       ├── migration.sql                # 마이그레이션 스크립트
│       ├── backup_script.sh             # 백업 스크립트
│       └── README.md                    # 데이터베이스 가이드
│
├── build.gradle                         # Gradle 빌드 설정
└── README.md                            # 프로젝트 문서
```

## 🚀 빠른 시작

### **사전 요구사항**
- Java 17 이상
- MariaDB 10.6+ 이상 (또는 MySQL 8.0+)
- Redis 6.0 이상 (선택사항, 환경 변수로 설정)
- RabbitMQ 3.x 이상 (선택사항, 환경 변수로 설정)
- Gradle 7.0 이상

### **1. 저장소 클론** (백엔드 전용 저장소 기준)
```bash
git clone https://github.com/<사용자명>/<백엔드-저장소명>.git
cd <백엔드-저장소명>
```
클론한 폴더가 곧 프로젝트 루트입니다(`investment-diary-api`라는 이름일 필요는 없음).

### **2. 데이터베이스 설정 (Flyway 전제)**

Flyway는 **테이블·스키마를 자동으로 적용**합니다. 아래는 **빈 DB와 계정만** 준비하면 됩니다.  
`src/main/resources/database/V*.sql`을 `mysql`로 직접 import하지 마세요. 이미 적용된 스크립트를 다시 넣으면 Flyway와 충돌할 수 있습니다.

```sql
-- MariaDB/MySQL 클라이언트에서 실행 (예: mysql -u root -p)
CREATE DATABASE investment_diary CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'investment_app'@'%' IDENTIFIED BY 'strong_password_here';
GRANT ALL PRIVILEGES ON investment_diary.* TO 'investment_app'@'%';
FLUSH PRIVILEGES;
```

- 로컬에서 `root`만 쓸 경우 사용자 생성 없이 `DB_USERNAME=root`와 해당 비밀번호를 환경 변수로 맞추면 됩니다.
- 이후 **4. 애플리케이션 실행**으로 기동하면 Flyway가 마이그레이션을 적용합니다.

### **3. 환경 설정**

#### **환경 변수 설정 (권장)**
프로젝트는 환경 변수로 설정을 주입합니다. Redis·RabbitMQ·WebAuthn 등 **추가 변수**는 저장소에 [ENV_SETUP.md](./ENV_SETUP.md)가 있을 때 참고하세요.

**최소 권장 (위에서 `investment_app`을 만든 경우):**
```bash
DB_HOST=localhost
DB_PORT=3306
DB_NAME=investment_diary
DB_USERNAME=investment_app
DB_PASSWORD=strong_password_here
JWT_SECRET=your_jwt_secret_key_at_least_256_bits
ENCRYPTION_KEY=your_encryption_key_at_least_256_bits
```

`DB_PORT`·`DB_NAME`을 생략하면 `application.yml` 기본값(`3306`, `investment_diary`)이 사용됩니다.

**선택 환경 변수 (Redis):**
```bash
REDIS_HOST=localhost  # 미설정 시 Redis 비활성, WebAuthn 챌린지 등은 인메모리 fallback
REDIS_PORT=6379
REDIS_PASSWORD=
```

#### **application.yml 설정 (대안)**
환경 변수를 사용하지 않는 경우 `src/main/resources/application.yml` 파일을 수정하세요.

### **4. 애플리케이션 실행**

#### **Spring Boot 내장 Tomcat으로 실행**
```bash
./gradlew bootRun
# Windows: gradlew.bat bootRun
```

#### **WAR 파일로 빌드하여 외부 Tomcat에서 실행**
```bash
# WAR 파일 빌드
./gradlew clean war

# 이클립스 Tomcat 서버에 배포
# 1. Servers 뷰에서 Tomcat 서버 우클릭
# 2. Add and Remove... 선택
# 3. investment-diary-api WAR 파일 추가
# 4. 서버 시작
```

애플리케이션이 `http://localhost:8080`에서 실행됩니다.

## 📚 API 문서

### **Swagger UI**
- **URL**: `http://localhost:8080/swagger-ui.html`
- **API 문서**: `http://localhost:8080/v3/api-docs`

### **API 명세서**
자세한 API 명세는 [API_SPECIFICATION.md](./API_SPECIFICATION.md) 파일을 참조하세요.

## 📊 데이터베이스 마이그레이션

### **Flyway 마이그레이션**
프로젝트는 Flyway로 스키마를 관리합니다. **빈 데이터베이스**에 처음 연결해 기동하면 `src/main/resources/database/`의 `V*.sql`이 버전 순으로 적용됩니다.

**주요 마이그레이션 (예시):**
- `V1__Create_initial_schema.sql`: 초기 스키마 생성
- `V18__Add_investment_seed_addition_to_monthly_actual_balances.sql`: 월별 투자시드 증액 컬럼 추가
- `V14__Add_currency_trading_support.sql`: 외환거래 지원 추가
  - `asset_type` 컬럼 추가 (STOCK, CURRENCY)
  - 외환거래 필드 추가 (currency_pair, base_currency, quote_currency, exchange_rate)
  - 인덱스 추가

**마이그레이션 실행:**
애플리케이션 시작 시 자동으로 실행됩니다. Gradle만으로 돌리려면:
```bash
./gradlew flywayMigrate
```
Windows에서는 `gradlew.bat flywayMigrate`를 사용합니다.

## 🔐 보안 기능

### **WebAuthn 인증 (비밀번호 없는 인증)**
- **FIDO2/WebAuthn 표준**: 최신 인증 표준 사용
- **다중 디바이스 지원**: 여러 인증기 등록 가능
- **보안 강화**: 피싱 및 비밀번호 유출 방지
- **Redis 기반 Challenge 저장**: 환경 변수로 Redis 설정 (없으면 메모리 fallback)

### **JWT 인증**
- **Access Token**: 24시간 유효
- **Refresh Token**: 7일 유효
- **자동 토큰 갱신**: 프론트엔드에서 자동 처리
- **레거시 지원**: 패스워드 기반 인증도 지원 (WebAuthn 전환 중)

### **데이터 암호화**
- **AES-256**: 민감 정보 (전화번호 등) 암호화
- **bcrypt**: 비밀번호 해싱
- **데이터베이스 레벨 암호화**: 저장 프로시저 사용

### **권한 관리**
- **USER**: 일반 사용자 권한
- **ADMIN**: 관리자 권한
- **역할 기반 접근 제어**: Spring Security 사용

### **REST-like API 보안 정책**
- **GET**: 데이터 조회만 허용
- **POST**: 모든 생성, 수정, 삭제 작업 처리
- **PUT/PATCH/DELETE 사용 금지**: 보안상의 이유로 사용하지 않음
- **장점**: 방화벽, 프록시 서버에서 HTTP 메서드 제한 시에도 안정적 동작

## 🧪 테스트

### **API 테스트**
```bash
# 사용자 등록
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "email": "test@example.com",
    "nickname": "테스트유저",
    "phone": "010-1234-5678"
  }'

# 로그인
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

### **단위 테스트**
```bash
./gradlew test
```

## 📊 데이터베이스

### **주요 테이블**
- **users**: 사용자 정보 (role, status, login_attempts 등, password nullable)
- **webauthn_credentials**: WebAuthn 인증기 정보 (credential_id, public_key, counter 등)
- **api_request_queue**: API 요청 큐 (request_id, status, retry_count, result_body 등)
- **investment_records**: 투자 기록 
  - 주식: type, stock_name, stock_code, quantity, price_per_share, total_amount 등
  - 외환: asset_type, currency_pair, base_currency, quote_currency, exchange_rate 등
  - 공통: current_price, unrealized_profit, realized_profit 등
- **portfolio_settings**: 포트폴리오 설정
- **user_sessions**: 사용자 세션 (session_id, access_token 등)
- **user_levels**: 사용자 레벨 및 포인트 (current_points, experience 등)
- **point_history**: 포인트 히스토리 (type, balance_after 등)
- **notifications**: 알림 (type, title, is_read 등)
- **community_posts**: 커뮤니티 게시글
- **community_comments**: 커뮤니티 댓글
- **asset_settings**: 자산 설정 (start_date, initial_balance, savings, investment_seed 등)
- **monthly_actual_balances**: 월별 실제 금액 (year, month, actual_balance, difference, **investment_seed_addition**)
- **fixed_incomes**: 고정 수입 (name, amount, day, start_date, end_date)
- **fixed_expenses**: 고정 지출 (name, amount, day, start_date, end_date)

### **스키마 동기화**
Entity와 DB가 어긋나면 **새 Flyway 버전 스크립트**(`Vn+1__....sql`)를 추가하는 방식으로 맞춥니다. 레거시 `database_alter_commands.sql` 등 수동 SQL 일괄 적용은 Flyway 이력과 맞지 않을 수 있으므로 사용하지 않는 것을 권장합니다. 실패한 마이그레이션은 Flyway `repair`·DB 백업 후 정리 등 운영 절차를 따르세요.

### **보안 기능**
- **AES-256 암호화**: 민감 정보 (전화번호 등) 보호
- **bcrypt 해싱**: 비밀번호 보안
- **JWT 토큰**: 세션 관리
- **사용자 접근 로그**: 보안 감사
- **자동 백업 및 복구**: 데이터 무결성 보장

## 🔧 개발 환경 설정

### **IDE 설정**
- **IntelliJ IDEA**: Spring Boot 프로젝트로 열기
- **Eclipse**: Gradle 프로젝트로 가져오기
- **VS Code**: Spring Boot Extension Pack 설치

### **디버깅**
```bash
# 디버그 모드로 실행
./gradlew bootRun --debug-jvm
```

## 📈 성능 최적화

### **캐싱 전략**
- **Redis**: 세션, 캐시 및 WebAuthn Challenge 저장 (환경 변수로 선택적 사용)
- **메모리 Fallback**: Redis 없을 때 메모리 Map 사용 (단일 서버 환경)
- **JPA 2nd Level Cache**: 엔티티 캐싱
- **Query Cache**: 자주 실행되는 쿼리 결과

### **데이터베이스 최적화**
- **인덱스**: 자주 조회되는 컬럼에 인덱스 적용
- **파티셔닝**: 대용량 테이블 파티셔닝
- **쿼리 최적화**: N+1 문제 방지

## 🔄 API 응답 구조

### **통일된 API 응답 형식 (UnifiedApiResponse)**
모든 API는 일관된 응답 구조를 사용합니다:

```json
{
  "success": true,
  "code": "SUCCESS",
  "codeNumber": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": { ... },
  "count": 1,
  "timestamp": "2025-01-18T10:30:00",
  "path": "/api/v1/auth/login"
}
```

**에러 응답:**
```json
{
  "success": false,
  "code": "INVALID_CREDENTIALS",
  "codeNumber": 401,
  "message": "사용자계정 또는 비밀번호가 올바르지 않습니다.",
  "data": null,
  "error": {
    "field": "credentials",
    "reason": "INVALID_CREDENTIALS",
    "message": "사용자계정 또는 비밀번호가 올바르지 않습니다."
  },
  "timestamp": "2025-01-18T10:30:00",
  "path": "/api/v1/auth/login"
}
```

## 📬 메시지 큐 시스템

### **개요**
API 요청을 메시지 큐로 처리하여 서버 장애 시에도 요청이 누락되지 않도록 보장하는 시스템입니다.

### **주요 기능**
- **이중 저장 구조**: DB에 먼저 저장 후 메시지 큐에 전송 (데이터 보존 보장)
- **자동 복구**: 서버 재시작 시 DB의 PENDING 요청 자동 복구
- **비동기 처리**: 백그라운드에서 요청 처리로 응답 시간 단축
- **자동 재시도**: 실패한 요청을 자동으로 재시도 (최대 재시도 횟수 설정 가능)
- **타임아웃 처리**: 처리 중인 요청의 타임아웃 감지 및 재시도
- **상태 조회**: 요청 ID로 처리 상태 및 결과 조회

### **메시지 큐 옵션**
- **RabbitMQ** (운영 환경 권장): 프로덕션 레벨 메시지 브로커, 지속성 큐 지원
- **Redis**: 기존 인프라 활용, 간단한 큐 구현
- **Memory** (개발 환경 기본값): 메모리 큐, 서버 재시작 시 소실되지만 DB에서 자동 복구

### **데이터 보존 보장**
- **DB 우선 저장**: 모든 요청은 DB에 먼저 저장되어 영구 보존
- **메시지 큐는 선택적**: 성능 향상을 위한 선택적 기능
- **자동 복구**: 서버 재시작 시 DB의 PENDING 요청 자동 복구
- **주기적 확인**: QueueConsumer가 5초마다 DB에서 PENDING 요청 확인

### **API 엔드포인트**

#### 1. 큐 제출
```
POST /api/v1/queue/submit
{
  "endpoint": "/api/v1/investment/create",
  "method": "POST",
  "body": { ... },
  "maxRetries": 3
}

Response:
{
  "requestId": "uuid",
  "status": "PENDING",
  "message": "요청이 큐에 추가되었습니다."
}
```

#### 2. 상태 조회
```
GET /api/v1/queue/status/{requestId}

Response:
{
  "requestId": "uuid",
  "status": "COMPLETED",
  "result": { ... },
  "processedAt": "2025-01-18T10:30:05"
}
```

### **환경 변수 설정**
```bash
# 메시지 큐 타입 (기본값: memory)
QUEUE_TYPE=memory  # rabbitmq, redis, memory

# RabbitMQ 설정 (QUEUE_TYPE=rabbitmq일 때)
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# Redis 설정은 기존 REDIS_HOST 활용 (QUEUE_TYPE=redis일 때)
REDIS_HOST=localhost

# 큐 설정
QUEUE_NAME=api_requests
QUEUE_MAX_RETRIES=3
QUEUE_RETRY_DELAY=5000  # 밀리초
QUEUE_PROCESSING_TIMEOUT=30  # 분
```

### **동작 방식**
1. **요청 제출**: Controller → QueueService → DB 저장 → 메시지 큐 전송
2. **백그라운드 처리**: QueueConsumer가 5초마다 DB의 PENDING 요청 처리
3. **자동 재시도**: 실패한 요청을 1분마다 확인하여 재시도
4. **서버 재시작**: DB의 PENDING 요청 자동 복구 및 처리

## 🚀 배포

### **Docker 배포**
```dockerfile
FROM openjdk:17-jre-slim
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### **클라우드 배포**
- **AWS**: EC2, RDS, ElastiCache
- **GCP**: Compute Engine, Cloud SQL, Memorystore
- **Azure**: Virtual Machines, Azure SQL, Azure Cache

## 🤝 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 라이센스

이 프로젝트는 MIT 라이센스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 📝 최근 변경사항 (2025년 1월 ~ 2026년)

### 2026년 (프론트엔드 연동)
- API 스펙 변경 없음. 기존 REST API가 다크모드·라이트모드 및 모바일 반응형을 적용한 최신 프론트엔드와 그대로 연동됩니다.
- WebAuthn, JWT, 투자/캘린더/자산 API 동작 및 응답 형식 유지.

### 2026년 3월 – 비밀번호 교체 주기 정책 추가
- **비밀번호 변경 권고(6개월)**:
  - 로그인/토큰갱신 응답에 `passwordChangeRecommended`, `passwordChangeDueAt`, `passwordChangeDeferredUntil` 필드가 추가되었습니다.
  - 마지막 비밀번호 변경 시각(`users.password_changed_at`) 기준 6개월이 지나면 `passwordChangeRecommended=true`가 될 수 있습니다.
  - WebAuthn 전용 등 **비밀번호가 없는 계정**은 해당 정책이 적용되지 않습니다.
- **3개월 유예**:
  - `POST /api/v1/auth/user/password/defer` 호출로 비밀번호 변경 권고를 3개월 유예할 수 있습니다.
- **최근 3개 비밀번호 재사용 금지**:
  - 비밀번호 변경 시 **현재 비밀번호 + 과거 이력 최대 2건**(총 최근 3개)과 동일한 평문은 사용할 수 없습니다.
- **DB 마이그레이션**:
  - Flyway `V20__Add_password_policy.sql`에서 `users.password_changed_at`, `users.password_change_deferred_until`, `user_password_history` 테이블이 추가됩니다.

### 2026년 3월 – 민감정보 Lazy 암호화 적용
- **엔티티 필드 암호화(점진 적용)**:
  - 기존 평문 데이터를 즉시 배치 마이그레이션하지 않고, 조회/저장 시점에 점진적으로 암호화되도록 적용.
  - `SensitiveTextAttributeConverter`를 통해 JPA 레벨에서 투명 암복호화 처리.
- **적용 컬럼(1차)**:
  - `investment_records.buy_reason`
  - `investment_records.sell_reason`
  - `fixed_incomes.name`
  - `fixed_expenses.name`
  - `period_incomes.name`
  - `period_expenses.name`
- **스키마 변경**:
  - Flyway `V21__Expand_name_columns_for_encryption.sql`로 이름 컬럼 길이를 암호문 저장 가능하도록 확장(`VARCHAR(255)`).
- **평문 호환 복호화 개선**:
  - 혼재 데이터(평문 + 암호문) 환경에서 `Illegal base64 character` 로그 노이즈가 발생하지 않도록
    `EncryptionUtil.decryptOrReturnPlain(...)`에 Base64 형태 사전 판별 로직을 추가.
  - Base64 형태가 아니면 복호화 시도 없이 평문 그대로 반환.
- **운영 지원용 암호화 API**:
  - 현재 설정된 서버 암호화 키 기준으로 입력 평문을 암호문으로 변환하는 운영 지원 API 추가
    (Swagger에서 수동 호출해 임시 데이터 패치에 활용 가능).

### 2026년 3월 – 월별 투자시드 증액 기능
- **월별 투자시드 증액(Investment Seed Addition)**:
  - 순수 현금이 일정 이상일 때 투자시드로 전환한 금액을 월별로 기록할 수 있도록 기능 추가.
  - **DB**: `monthly_actual_balances` 테이블에 `investment_seed_addition` 컬럼 추가 (Flyway V18).
  - **엔티티**: `MonthlyActualBalance`에 `investmentSeedAddition` 필드 추가.
  - **총 시드 재계산**: 홈 포트폴리오의 총 시드 = 자산 설정의 **초기 투자시드** + 월별 `investment_seed_addition` 합계. 저장/삭제 시 자동 재계산.
  - **API**: `POST /api/v1/asset/monthly-investment-seed-addition` (year, month, amount 선택). amount 생략 또는 0이면 해당 월 증액 제거.
  - **Repository**: `MonthlyActualBalanceRepository.findByUser(User)` 추가 (총시드 재계산용).
  - **AssetService**: `saveMonthlyInvestmentSeedAddition`, `recomputeAndUpdatePortfolioTotalSeed` 추가. 월별 실제 금액 삭제 시에도 총시드 재계산 호출.

### 2025년 1월 변경사항

### 주식 데이터 관리 개선
- **과거 종가 데이터 채우기 기능**:
  - 매수 기록 생성 시 해당 매수일부터 오늘까지 종가 데이터 자동 조회
  - 종가 정보는 종목별 공유 데이터로 효율적 관리
  - `POST /api/v1/stock-data/fill-history/{stockCode}`: 특정 종목의 과거 종가 채우기
  - `POST /api/v1/stock-data/fill-all-history`: 모든 보유 종목의 과거 종가 채우기
  - 중복 방지: 이미 DB에 있는 날짜는 건너뜀
- **주말/휴장일 처리**:
  - 한국 주식: 토요일/일요일에는 수익률 변화량 계산하지 않음
  - 미국 주식: 주말에도 종가 데이터가 있으면 수익률 변화량 계산
  - 시장별 휴장일 자동 감지

### 배당 정보 자동 조회
- **DART (전자공시시스템) API 통합**:
  - 한국 주식의 공식 배당 정보 조회
  - `OpenDartReader` 라이브러리 사용
  - `alotMatter.json` API를 통한 구조화된 배당 데이터 조회
  - 최근 3년간의 배당 정보를 조회하여 최신 정보 사용
- **배당 정보 우선순위**:
  - 한국 주식: DART → Naver Finance
  - 미국 주식: Alpha Vantage
- **배당일 자동 추출**: 배당 기준일 정보 자동 저장

### 캘린더 기능 개선
- **전날 대비 평단가 기준 수익률 변화량 계산**:
  - 각 날짜에 보유 종목의 전날 대비 수익률 변화량을 가중 평균으로 계산
  - 전날 종가와 오늘 종가를 비교하여 평단가 기준 수익률 변화량 계산
  - 보유 수량과 평단가를 가중치로 사용한 가중 평균 계산
  - 투자 기록이 없는 날짜에도 보유 종목이 있으면 자동 계산
  - 한국 주식은 주말에 계산하지 않음
- **일별 보유 종목 조회 API 추가** (`GET /api/v1/calendar/{date}/portfolio`):
  - 특정 날짜의 보유 종목 목록과 상세 정보 조회
  - 각 종목별 전날 대비 수익률 변화량, 최종 수익률, 수익금 계산
  - 환율 적용하여 사용자 통화로 변환
  - 보유 수량, 평균 단가 정보 포함
- **캘린더 데이터 API 개선**:
  - `profitRateChange` 필드 추가 (전날 대비 수익률 변화량)
  - 투자 기록이 없는 날짜에도 보유 종목이 있으면 수익률 변화량 계산

### 외환거래 기능 추가
- **자산 유형 지원**: 주식(STOCK)과 외환(CURRENCY) 구분
- **외환거래 기록**: 
  - 통화쌍 입력 (예: USD/KRW, EUR/USD)
  - 기준통화/상대통화 입력
  - 환율 입력 및 관리
  - 외환 수량 입력 (기준 통화 기준)
- **외환거래 계산 로직**:
  - 총 투자금액 = 수량 × 환율 (원화로 환산)
  - 평균 환율 자동 계산
- **데이터베이스 마이그레이션**: V14 추가
  - `asset_type` 컬럼 추가
  - 외환거래 필드 추가 (currency_pair, base_currency, quote_currency, exchange_rate)
  - 인덱스 추가
- **API 검증 개선**: 자산 유형에 따른 조건부 검증
  - 주식: stockName 필수
  - 외환: currencyPair 또는 (baseCurrency + quoteCurrency) 필수, exchangeRate 필수

## 📚 추가 문서

- **[ENV_SETUP.md](./ENV_SETUP.md)**: 환경 변수 설정 가이드
- **[API_SPECIFICATION.md](./API_SPECIFICATION.md)**: API 명세서

## 📞 문의

- **이메일**: dev@investmentdiary.com
- **프로젝트 링크**: [https://github.com/your-username/investment-diary-api](https://github.com/your-username/investment-diary-api)

## 🙏 감사의 말

이 프로젝트는 다음과 같은 오픈소스 프로젝트들을 기반으로 합니다:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring Security](https://spring.io/projects/spring-security)
- [MySQL](https://www.mysql.com/)
- [Redis](https://redis.io/)

---

**⭐ 이 프로젝트가 도움이 되었다면 스타를 눌러주세요!** 