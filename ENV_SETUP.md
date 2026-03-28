# 환경 변수 설정 가이드

이 프로젝트는 환경 변수를 통해 설정을 관리합니다. 개발 환경에서는 필수 설정만 입력하면 되고, 운영 환경에서는 모든 설정을 입력해야 합니다.

## 필수 환경 변수

### 데이터베이스 설정
```bash
# MySQL/MariaDB 설정
DB_HOST=192.168.0.100          # 데이터베이스 호스트
DB_PORT=3306                    # 데이터베이스 포트
DB_NAME=investment_diary        # 데이터베이스 이름
DB_USERNAME=investment_app      # 데이터베이스 사용자명
DB_PASSWORD=your_password       # 데이터베이스 비밀번호
```

### JWT 설정
```bash
JWT_SECRET=your_jwt_secret_key_here_make_it_very_long_and_secure_at_least_256_bits
```

### 암호화 설정
```bash
ENCRYPTION_KEY=your_encryption_key_here_make_it_very_long_and_secure_at_least_256_bits
```

## 선택적 환경 변수

### Redis 설정 (WebAuthn Challenge 저장용)
Redis는 WebAuthn Challenge 저장에 사용됩니다. 설정하지 않으면 메모리 Map을 사용합니다.

**필수 환경 변수:**
- `REDIS_HOST`: Redis 호스트 주소 (예: localhost, 192.168.0.100)
  - **설정하지 않으면 Redis 비활성화되고 메모리 Map을 사용합니다**

**선택적 환경 변수 (기본값 있음):**
```bash
# 필수: Redis 호스트만 설정하면 기본값으로 동작합니다
REDIS_HOST=localhost            # 필수: Redis 호스트 주소

# 선택: 아래 값들은 기본값이 있어서 설정하지 않아도 됩니다
REDIS_PORT=6379                 # 선택: 기본값 6379
REDIS_PASSWORD=                 # 선택: 기본값 빈 문자열 (비밀번호 없으면 설정 불필요)
REDIS_DATABASE=0                # 선택: 기본값 0 (DB명 설정 불필요)
REDIS_TIMEOUT=2000ms            # 선택: 기본값 2000ms

# Redis Pool 설정 (모두 선택, 기본값 있음)
REDIS_POOL_MAX_ACTIVE=8         # 선택: 기본값 8
REDIS_POOL_MAX_IDLE=8           # 선택: 기본값 8
REDIS_POOL_MIN_IDLE=0           # 선택: 기본값 0
REDIS_POOL_MAX_WAIT=-1ms        # 선택: 기본값 -1ms
```

**요약:**
- **최소 설정**: `REDIS_HOST=localhost`만 설정하면 됩니다
- 비밀번호가 없는 Redis: `REDIS_PASSWORD` 설정 불필요
- 기본 DB 사용: `REDIS_DATABASE` 설정 불필요

### WebAuthn 설정
```bash
WEBAUTHN_RP_ID=localhost                    # Relying Party ID
WEBAUTHN_RP_NAME=투자일지                    # Relying Party 이름
WEBAUTHN_ORIGIN=http://localhost:3000       # 프론트엔드 Origin
WEBAUTHN_TIMEOUT=60000                      # Challenge 타임아웃 (밀리초)
WEBAUTHN_CHALLENGE_SIZE=32                  # Challenge 크기 (바이트)
WEBAUTHN_CHALLENGE_REDIS_ENABLED=false      # Redis 사용 여부 (true/false)
WEBAUTHN_CHALLENGE_TTL=300                  # Challenge TTL (초)
```

### 캐시 설정
```bash
CACHE_TYPE=simple              # simple 또는 redis
CACHE_TTL=300000               # 캐시 TTL (밀리초)
```

### 메시지 큐 설정 (API 요청 큐 처리용)
메시지 큐는 API 요청을 비동기로 처리하여 서버 장애 시에도 요청이 누락되지 않도록 보장합니다.

```bash
# 메시지 큐 타입 선택 (rabbitmq, redis, memory)
QUEUE_TYPE=memory              # 기본값: memory (개발 환경)
QUEUE_NAME=api_requests        # 큐 이름
QUEUE_DURABLE=true             # 큐 지속성
QUEUE_MAX_RETRIES=3            # 최대 재시도 횟수
QUEUE_RETRY_DELAY=5000         # 재시도 지연 시간 (밀리초)
QUEUE_PROCESSING_TIMEOUT=30    # 처리 타임아웃 (분)

# RabbitMQ 설정 (QUEUE_TYPE=rabbitmq일 때 사용)
RABBITMQ_HOST=localhost        # RabbitMQ 호스트
RABBITMQ_PORT=5672             # RabbitMQ 포트
RABBITMQ_USERNAME=guest        # RabbitMQ 사용자명
RABBITMQ_PASSWORD=guest        # RabbitMQ 비밀번호
RABBITMQ_VHOST=/               # RabbitMQ Virtual Host

# Redis 설정은 기존 REDIS_HOST 환경 변수 활용 (QUEUE_TYPE=redis일 때)
```

## 개발 환경 설정 예시

### Windows (PowerShell)
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="investment_diary"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:JWT_SECRET="development_jwt_secret_key_here"
$env:ENCRYPTION_KEY="development_encryption_key_here"
# Redis는 선택사항 (REDIS_HOST만 설정하면 됨, 비밀번호/DB명 불필요)
# $env:REDIS_HOST="localhost"  # Redis 사용 시에만 설정
```

### Linux/Mac (Bash)
```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=investment_diary
export DB_USERNAME=root
export DB_PASSWORD=your_password
export JWT_SECRET=development_jwt_secret_key_here
export ENCRYPTION_KEY=development_encryption_key_here
# Redis는 선택사항 (REDIS_HOST만 설정하면 됨, 비밀번호/DB명 불필요)
# export REDIS_HOST="localhost"  # Redis 사용 시에만 설정
```

### .env 파일 사용 (권장)
프로젝트 루트에 `.env` 파일을 생성하고 위의 환경 변수를 입력하세요.

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=investment_diary
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=development_jwt_secret_key_here
ENCRYPTION_KEY=development_encryption_key_here
```

## 운영 환경 설정 예시

운영 환경에서는 모든 설정을 환경 변수로 입력해야 합니다.

```bash
# 데이터베이스
DB_HOST=production-db.example.com
DB_PORT=3306
DB_NAME=investment_diary
DB_USERNAME=production_user
DB_PASSWORD=secure_production_password

# JWT 및 암호화
JWT_SECRET=production_jwt_secret_key_very_long_and_secure
ENCRYPTION_KEY=production_encryption_key_very_long_and_secure

# Redis (운영 환경에서는 권장)
REDIS_HOST=production-redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=secure_redis_password
REDIS_DATABASE=0

# WebAuthn
WEBAUTHN_RP_ID=example.com
WEBAUTHN_RP_NAME=투자일지
WEBAUTHN_ORIGIN=https://example.com
WEBAUTHN_CHALLENGE_REDIS_ENABLED=true

# 캐시
CACHE_TYPE=redis
CACHE_TTL=300000
```

## 주의사항

1. **Redis가 없어도 동작**: Redis를 설정하지 않으면 메모리 Map을 사용합니다. 단일 서버 환경에서는 문제없지만, 여러 서버 환경에서는 Redis를 사용해야 합니다.

2. **보안**: 운영 환경에서는 반드시 강력한 비밀번호와 키를 사용하세요.

3. **환경 변수 우선순위**: 환경 변수가 설정되어 있으면 `application.yml`의 기본값보다 우선합니다.

