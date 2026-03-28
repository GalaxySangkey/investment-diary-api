# DB 개인정보 암호화

## 암호화 알고리즘

- **알고리즘**: AES (Java `Cipher.getInstance("AES")` → **AES/ECB/PKCS5Padding**)
- **키 길이**: 128비트 (16바이트)
- **키 파생**: 설정된 암호화 키 문자열을 **SHA-256** 해시한 뒤 앞 16바이트를 AES 키로 사용
- **저장 형식**: 암호문을 **Base64** 인코딩하여 DB VARCHAR 컬럼에 저장

> application.yml에는 `encryption.algorithm: AES/CBC/PKCS5Padding`이 있으나,  
> 현재 `EncryptionUtil`은 코드에서 `"AES"`만 사용하므로 **ECB** 모드가 적용됩니다.  
> CBC 등으로 변경하려면 `EncryptionUtil`에서 `ALGORITHM` 및 IV 처리를 수정해야 합니다.

---

## 암호 키 생성 및 저장 위치

- **키 값**: 운영/개발에서 **직접 정하는 문자열** (예: 32자 이상 권장)
- **저장 위치**  
  1. **환경 변수** (권장): `ENCRYPTION_KEY=your_encryption_key_here_make_it_very_long_and_secure_at_least_256_bits`  
  2. **application.yml**: `encryption.key: ${ENCRYPTION_KEY:기본값}`  
     - 기본값은 로컬용 예시일 뿐, **운영에서는 반드시 환경 변수로 덮어쓰기**
- **관련 문서**: [ENV_SETUP.md](./ENV_SETUP.md) 참고

키는 **코드/저장소에 평문으로 커밋하지 말고**, 배포 환경의 환경 변수 또는 비밀 관리 도구에만 두는 것이 안전합니다.

---

## AttributeConverter와 대량 조회 시 부하

### 동작 방식

- `UserNameAttributeConverter`는 **엔티티 1건당 1번** 호출됩니다.
- User를 **N건** 조회하면:
  - **N번** `convertToEntityAttribute` 호출 → **N번 복호화** 발생
- 따라서 **건수에 비례한 CPU 부하**가 있습니다 (건당 암복호화 1회).

### 부하가 큰지 여부

- **AES 복호화** 자체는 건당 수 ~수십 μs 수준으로 매우 빠른 편입니다.
- **키 파생(SHA-256)** 은 `EncryptionUtil`에서 **캐시**하므로, 복호화 시에는 키 생성 오버헤드는 한 번만 발생합니다.
- 그래서:
  - **1건, 수십 건**: 체감 부하 거의 없음
  - **100건**: 보통 수 ms ~ 수십 ms 수준
  - **1000건**: 수십 ms ~ 100 ms 근처까지 갈 수 있음 (환경에 따라 상이)

즉, **“부하가 전혀 없다”**기보다는 **“건수에 비례한 작은 CPU 비용”**이 든다고 보는 것이 맞습니다.

### 권장 사항

- **일반적인 사용자 단위 조회**(로그인, 마이페이지, 1명 상세 등): 그대로 사용해도 무방
- **관리자 등에서 User를 수백~수천 건 한 번에 조회**하는 API가 있다면:
  - 목록용 DTO에서는 `name`을 **마스킹**하거나 제외하고,
  - 상세/다운로드 등 필요한 경우에만 복호화하도록 나누는 것을 고려할 수 있습니다.
- 필요 시 목록 API는 `name`을 제외한 DTO로 응답하고, 상세 API에서만 `User` 엔티티를 그대로 사용하는 방식으로 분리하면 됩니다.

---

## 적용 대상

| 필드           | 암호화 여부 | 비고 |
|----------------|------------|------|
| User.name      | ✅ 적용    | AttributeConverter |
| User.phone     | ✅ 적용    | 기존 `phone_encrypted` 컬럼, 저장 시 암호화 |
| User.email     | ❌ 미적용  | 로그인/조회용으로 평문 유지 |
| User.nickname  | ❌ 미적용  | 검색(LIKE)용으로 평문 유지 |
| InvestmentRecord.buy_reason | ✅ 적용 | `SensitiveTextAttributeConverter` (lazy) |
| InvestmentRecord.sell_reason | ✅ 적용 | `SensitiveTextAttributeConverter` (lazy) |
| FixedIncome.name | ✅ 적용 | `SensitiveTextAttributeConverter` (lazy) |
| FixedExpense.name | ✅ 적용 | `SensitiveTextAttributeConverter` (lazy) |
| PeriodIncome.name | ✅ 적용 | `SensitiveTextAttributeConverter` (lazy) |
| PeriodExpense.name | ✅ 적용 | `SensitiveTextAttributeConverter` (lazy) |

---

## Lazy 적용 정책 (평문/암호문 혼재 대응)

- 이번 6개 컬럼은 **일괄 재암호화 배치 없이** lazy 방식으로 적용했습니다.
- 동작 방식:
  - 저장 시: 암호화되어 DB 저장
  - 조회 시: 복호화 시도 후 실패하면 평문 그대로 반환 (`decryptOrReturnPlain`)
  - Base64 형태가 아닌 값은 복호화 시도 없이 평문으로 즉시 반환 (로그 노이즈 방지)
- 결과적으로 기존 평문 데이터는 조회 시 깨지지 않고, 해당 레코드가 수정/재저장될 때 점진적으로 암호문으로 전환됩니다.

### 관련 마이그레이션

- `V21__Expand_name_columns_for_encryption.sql`
  - `fixed_incomes.name`, `fixed_expenses.name`, `period_incomes.name`, `period_expenses.name` 컬럼 길이 `VARCHAR(255)` 확장

## 비밀번호 정책 (로그인 계정)

데이터 필드 암호화와 별도로, **비밀번호 해시**에 대한 운영 정책은 다음과 같습니다.

- **변경 권고**: 마지막 변경(`password_changed_at`) 후 **6개월**이 지나면 로그인·토큰 갱신 응답의 `passwordChangeRecommended`가 `true`일 수 있습니다. 비밀번호가 없는 계정(WebAuthn 전용 등)은 해당 없음.
- **3개월 유예**: `POST /api/v1/auth/user/password/defer`로 유예 시 `passwordChangeDeferredUntil`까지 권고를 숨깁니다.
- **재사용 금지**: 변경 시 **현재 비밀번호**와 이력 **최대 2건**(합쳐 최근 3개)과 동일한 평문은 사용할 수 없습니다.
- **DB**: `users.password_changed_at`, `users.password_change_deferred_until`, 테이블 `user_password_history`.

**암호화 키 로테이션**(다중 키, Vault 등)은 현재 범위에서 제외이며, `encryption.key` 단일 키를 유지합니다.

