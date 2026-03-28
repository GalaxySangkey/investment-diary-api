# 📊 투자일지 API 명세서

프론트엔드 개발자를 위한 투자일지 API 상세 명세서입니다.

## 📋 목차

1. [기본 정보](#기본-정보)
2. [인증 및 보안](#인증-및-보안)
3. [공통 응답 형식](#공통-응답-형식)
4. [인증 API](#인증-api)
5. [투자 기록 API](#투자-기록-api)
6. [포트폴리오 API](#포트폴리오-api)
7. [캘린더 API](#캘린더-api)
8. [자산관리·기타 API 요약](#자산관리기타-api-요약)
9. [에러 코드](#에러-코드)
10. [프론트엔드 연동 가이드](#프론트엔드-연동-가이드)

## 🔧 기본 정보

### API 기본 URL
```
개발 환경: http://localhost:8080/api/v1
운영 환경: https://api.investmentdiary.com/api/v1
```

### Content-Type
```
application/json
```

### API 버전
```
v1
```

## 🔐 인증 및 보안

### JWT 토큰
- **Access Token**: 24시간 유효
- **Refresh Token**: 7일 유효
- **알고리즘**: HS256

### REST-like API 보안 정책
**PUT, PATCH, DELETE HTTP 메서드는 사용하지 않습니다.** (서버 구현·CORS 모두 GET·POST 중심)
- **GET**: 데이터 조회
- **POST**: 생성·수정·삭제는 각각 전용 경로로 처리 (예: `.../update`, `.../delete`, `investments/update`, `investments/delete`)
- **CORS 허용 메서드**: `GET`, `POST`, `OPTIONS`, `HEAD`
- **CORS 허용 Origin**: 환경 변수 **`CORS_ALLOWED_ORIGINS`**(쉼표 구분) 또는 `application.yml`의 **`app.cors.allowed-origins`**. 쿠키 인증 사용 시 `*` 사용 불가.

상세·전체 목록은 앱 기동 후 **Swagger UI**(`springdoc`)에서 확인하는 것이 가장 정확합니다.

### 헤더 설정
```javascript
// 인증이 필요한 API 요청 시
headers: {
  'Authorization': 'Bearer <access_token>',
  'Content-Type': 'application/json',
  'X-Device-UUID': '<device_identifier>',
  'X-API-Version': 'v1'
}
```

### 토큰 갱신
```javascript
// Access Token 만료 시 Refresh Token으로 갱신
const refreshToken = async (refreshToken) => {
  const response = await axios.post('/api/v1/auth/refresh', {}, {
    headers: {
      'Authorization': `Bearer ${refreshToken}`
    }
  });
  return response.data.data;
};
```

## 📊 공통 응답 형식

### 성공 응답
```json
{
  "success": true,
  "data": {
    // 실제 데이터
  },
  "message": "요청이 성공적으로 처리되었습니다.",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 에러 응답
```json
{
  "success": false,
  "error": {
    "code": "INVALID_INPUT",
    "message": "입력값이 올바르지 않습니다.",
    "details": {
      "field": "email",
      "reason": "올바른 이메일 형식이 아닙니다."
    }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 페이징 응답
```json
{
  "success": true,
  "data": {
    "content": [
      // 데이터 배열
    ],
    "totalElements": 100,
    "totalPages": 5,
    "currentPage": 0,
    "size": 20,
    "first": true,
    "last": false
  }
}
```

## 🔑 인증 API

### 1. 회원가입

**엔드포인트**: `POST /api/v1/auth/register`

**요청 본문**:
```json
{
  "username": "user123",
  "password": "SecurePass123!",
  "email": "user@example.com",
  "nickname": "투자왕",
  "phone": "010-1234-5678"
}
```

**응답**:
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "username": "user123",
    "nickname": "투자왕",
    "message": "회원가입이 완료되었습니다."
  }
}
```

**JavaScript 예시**:
```javascript
const register = async (userData) => {
  try {
    const response = await axios.post('/api/v1/auth/register', userData);
    return response.data.data;
  } catch (error) {
    console.error('회원가입 실패:', error.response.data);
    throw error;
  }
};
```

### 2. 로그인

**엔드포인트**: `POST /api/v1/auth/login`

**요청 본문**:
```json
{
  "username": "user123",
  "password": "SecurePass123!",
  "deviceInfo": "iPhone 14, iOS 16.0"
}
```

**응답**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "username": "user123",
      "nickname": "투자왕",
      "email": "user@example.com",
      "role": "USER",
      "status": "ACTIVE",
      "emailVerified": true,
      "phoneVerified": false,
      "lastLoginAt": "2024-01-15T10:30:00Z"
    }
  }
}
```

**JavaScript 예시**:
```javascript
const login = async (username, password) => {
  try {
    const response = await axios.post('/api/v1/auth/login', {
      username,
      password,
      deviceInfo: navigator.userAgent
    });
    
    const { accessToken, refreshToken, user } = response.data.data;
    
    // 토큰 저장
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    
    // axios 헤더 설정
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
    
    return user;
  } catch (error) {
    console.error('로그인 실패:', error.response.data);
    throw error;
  }
};
```

### 3. 토큰 갱신

**엔드포인트**: `POST /api/v1/auth/refresh`

**헤더**:
```
Authorization: Bearer <refresh_token>
```

**응답**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  }
}
```

### 4. 로그아웃

**엔드포인트**: `POST /api/v1/auth/logout`

**헤더**:
```
Authorization: Bearer <access_token>
```

**응답**:
```json
{
  "success": true,
  "data": null,
  "message": "로그아웃이 완료되었습니다."
}
```

## 💰 투자 기록 API

### 1. 투자 기록 목록 조회

**엔드포인트**: `GET /api/v1/investments`

**쿼리 파라미터**:
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지 크기 (기본값: 20)
- `type`: 투자 유형 (`buy` | `sell`)
- `startDate`: 시작 날짜 (YYYY-MM-DD)
- `endDate`: 종료 날짜 (YYYY-MM-DD)

**헤더**:
```
Authorization: Bearer <access_token>
```

**응답**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "recordDate": "2024-01-15",
        "type": "buy",
        "stockName": "삼성전자",
        "stockCode": "005930",
        "investmentRatio": 15.5,
        "quantity": 10,
        "pricePerShare": 75000,
        "totalAmount": 750000,
        "dividendPerShare": 1500,
        "dividendRatio": 2.0,
        "buyReason": "반도체 시장 회복 기대",
        "currentPrice": 78000,
        "unrealizedProfitRate": 4.0,
        "unrealizedProfitAmount": 30000,
        "isDeleted": false,
        "createdAt": "2024-01-15T09:00:00Z"
      }
    ],
    "totalElements": 25,
    "totalPages": 2,
    "currentPage": 0,
    "size": 20
  }
}
```

**JavaScript 예시**:
```javascript
const getInvestments = async (params = {}) => {
  try {
    const response = await axios.get('/api/v1/investments', { params });
    return response.data.data;
  } catch (error) {
    console.error('투자 기록 조회 실패:', error.response.data);
    throw error;
  }
};

// 사용 예시
const investments = await getInvestments({
  page: 0,
  size: 20,
  type: 'buy',
  startDate: '2024-01-01',
  endDate: '2024-01-31'
});
```

### 2. 투자 기록 상세 조회

**엔드포인트**: `GET /api/v1/investments/{id}`

**응답**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "recordDate": "2024-01-15",
    "type": "buy",
    "stockName": "삼성전자",
    "stockCode": "005930",
    "investmentRatio": 15.5,
    "quantity": 10,
    "pricePerShare": 75000,
    "totalAmount": 750000,
    "dividendPerShare": 1500,
    "dividendRatio": 2.0,
    "buyReason": "반도체 시장 회복 기대",
    "currentPrice": 78000,
    "profitRate": 4.0,
    "profitAmount": 30000,
    "createdAt": "2024-01-15T09:00:00Z",
    "updatedAt": "2024-01-15T09:00:00Z"
  }
}
```

### 3. 매수 기록 생성

**엔드포인트**: `POST /api/v1/investments/buy`

**요청 본문**:
```json
{
  "recordDate": "2024-01-15",
  "stockName": "삼성전자",
  "stockCode": "005930",
  "investmentRatio": 15.5,
  "quantity": 10,
  "pricePerShare": 75000,
  "dividendPerShare": 1500,
  "buyReason": "반도체 시장 회복 기대"
}
```

**JavaScript 예시**:
```javascript
const createBuyInvestment = async (investmentData) => {
  try {
    const response = await axios.post('/api/v1/investments/buy', investmentData);
    return response.data.data;
  } catch (error) {
    console.error('매수 기록 생성 실패:', error.response.data);
    throw error;
  }
};
```

### 4. 매도 기록 생성

**엔드포인트**: `POST /api/v1/investments/sell`

**요청 본문**:
```json
{
  "recordDate": "2024-01-20",
  "selectedStockId": 1,
  "sellQuantity": 5,
  "sellRatio": 50.0,
  "realizedProfitRate": 8.5,
  "sellReason": "목표 수익률 달성"
}
```

### 5. 투자 기록 수정

**엔드포인트**: `POST /api/v1/investments/update`

**요청 본문**:
```json
{
  "id": 1,
  "investmentRatio": 16.0,
  "quantity": 12,
  "pricePerShare": 76000,
  "buyReason": "수정된 매수 이유"
}
```

**응답**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "message": "투자 기록이 수정되었습니다."
  }
}
```

### 6. 투자 기록 삭제

**엔드포인트**: `POST /api/v1/investments/delete`

**요청 본문**:
```json
{
  "id": 1
}
```

**응답**:
```json
{
  "success": true,
  "data": null,
  "message": "투자 기록이 삭제되었습니다."
}
```

## 📈 포트폴리오 API

### 1. 포트폴리오 요약 조회

**엔드포인트**: `GET /api/v1/portfolio/summary`

**응답**:
```json
{
  "success": true,
  "data": {
    "totalSeed": 10000000,
    "totalInvestment": 8500000,
    "totalProfitRate": 12.5,
    "totalDividendRate": 3.2,
    "totalProfitAmount": 1062500,
    "totalDividendAmount": 272000,
    "lastUpdated": "2024-01-15T10:30:00Z"
  }
}
```

**JavaScript 예시**:
```javascript
const getPortfolioSummary = async () => {
  try {
    const response = await axios.get('/api/v1/portfolio/summary');
    return response.data.data;
  } catch (error) {
    console.error('포트폴리오 요약 조회 실패:', error.response.data);
    throw error;
  }
};
```

### 2. 포트폴리오 설정 조회

**엔드포인트**: `GET /api/v1/portfolio/settings`

**응답**:
```json
{
  "success": true,
  "data": {
    "totalSeed": 10000000,
    "currency": "KRW",
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

### 3. 포트폴리오 설정 수정

**엔드포인트**: `POST /api/v1/portfolio/settings/update`

**요청 본문**:
```json
{
  "totalSeed": 15000000,
  "currency": "KRW"
}
```

**응답**:
```json
{
  "success": true,
  "data": {
    "totalSeed": 15000000,
    "currency": "KRW",
    "message": "포트폴리오 설정이 수정되었습니다."
  }
}
```

## 📅 캘린더 API

### 1. 월별 캘린더 데이터 조회

**엔드포인트**: `GET /api/v1/calendar`

**쿼리 파라미터**:
- `year`: 년도 (기본값: 현재 년도)
- `month`: 월 (기본값: 현재 월)

**응답**:
```json
{
  "success": true,
  "data": {
    "year": 2024,
    "month": 1,
    "days": [
      {
        "date": "2024-01-15",
        "profitRate": 4.2,
        "hasInvestment": true,
        "recordCount": 2,
        "totalInvestment": 1500000
      },
      {
        "date": "2024-01-20",
        "profitRate": -1.5,
        "hasInvestment": true,
        "recordCount": 1,
        "totalInvestment": 750000
      }
    ]
  }
}
```

**JavaScript 예시**:
```javascript
const getCalendarData = async (year, month) => {
  try {
    const response = await axios.get('/api/v1/calendar', {
      params: { year, month }
    });
    return response.data.data;
  } catch (error) {
    console.error('캘린더 데이터 조회 실패:', error.response.data);
    throw error;
  }
};
```

### 2. 특정 날짜 투자 기록 조회

**엔드포인트**: `GET /api/v1/calendar/{date}/records` (`date`: `YYYY-MM-DD`)

**응답**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "type": "buy",
      "stockName": "삼성전자",
      "investmentRatio": 15.5,
      "quantity": 10,
      "pricePerShare": 75000,
      "buyReason": "반도체 시장 회복 기대"
    }
  ]
}
```

### 3. 특정 날짜 보유 종목(포트폴리오) 조회

**엔드포인트**: `GET /api/v1/calendar/{date}/portfolio`  
**응답 형식**: Swagger UI 또는 실제 호출 결과를 참고하세요.

## 자산관리·기타 API 요약

### 자산관리 `AssetController` (`/api/v1/asset`)
- `GET/POST /asset/settings` — 자산 설정 조회·저장
- `GET/POST /asset/fixed-incomes` — 고정 수입 목록·생성
- `POST /asset/fixed-incomes/{id}/update` — 수정
- `POST /asset/fixed-incomes/{id}/delete` — 삭제
- `GET/POST /asset/fixed-expenses` — 고정 지출 목록·생성
- `POST /asset/fixed-expenses/{id}/update`, `POST .../delete`
- `GET /asset/monthly-balance/{year}/{month}`, `GET /asset/monthly-balance/{year}`
- `POST /asset/monthly-balance` — 월별 실제 금액 저장
- `POST /asset/monthly-balance/delete` — 쿼리 `year`, `month`
- `POST /asset/monthly-investment-seed-addition` — 월별 투자시드 증액

### 주가·종목 `StockDataController` (`/api/v1/stock-data`)
- `GET /stock-data/stocks/search` — 종목 검색 (쿼리 파라미터는 Swagger 참고)
- 배치·갱신용 `POST` 엔드포인트 (`update-prices`, `fill-history` 등) — 운영/관리 목적

### 문의 `ContactController`
- `POST /api/v1/contact/inquiries` — 문의 접수

### 큐 `QueueController`
- `POST /api/v1/queue/submit`, `GET /api/v1/queue/status/{requestId}`

## ❌ 에러 코드

### 인증 관련 에러
- `AUTHENTICATION_FAILED`: 인증 실패
- `TOKEN_EXPIRED`: 토큰 만료
- `INVALID_TOKEN`: 유효하지 않은 토큰
- `ACCOUNT_LOCKED`: 계정 잠금

### 입력값 관련 에러
- `INVALID_INPUT`: 잘못된 입력값
- `MISSING_REQUIRED_FIELD`: 필수 필드 누락
- `INVALID_FORMAT`: 잘못된 형식

### 비즈니스 로직 에러
- `INSUFFICIENT_BALANCE`: 잔액 부족
- `STOCK_NOT_FOUND`: 종목을 찾을 수 없음
- `DUPLICATE_RECORD`: 중복 기록

### 시스템 에러
- `INTERNAL_SERVER_ERROR`: 내부 서버 오류
- `SERVICE_UNAVAILABLE`: 서비스 불가
- `DATABASE_ERROR`: 데이터베이스 오류

## 🔗 프론트엔드 연동 가이드

### 1. 기본 설정

```javascript
// API 기본 설정
const API_CONFIG = {
  baseURL: 'http://localhost:8080/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    'X-API-Version': 'v1'
  }
};

// axios 인스턴스 생성
const apiClient = axios.create(API_CONFIG);

// 요청 인터셉터 (토큰 자동 추가)
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터 (토큰 갱신)
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    if (error.response.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const response = await axios.post('/api/v1/auth/refresh', {}, {
          headers: { Authorization: `Bearer ${refreshToken}` }
        });
        
        const { accessToken } = response.data.data;
        localStorage.setItem('accessToken', accessToken);
        
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        // 리프레시 토큰도 만료된 경우 로그아웃
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);
```

### 2. API 서비스 클래스

```javascript
class InvestmentApiService {
  // 인증 관련
  async register(userData) {
    const response = await apiClient.post('/auth/register', userData);
    return response.data.data;
  }
  
  async login(credentials) {
    const response = await apiClient.post('/auth/login', credentials);
    const { accessToken, refreshToken, user } = response.data.data;
    
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    
    return user;
  }
  
  async logout() {
    await apiClient.post('/auth/logout');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  }
  
  // 투자 기록 관련
  async getInvestments(params = {}) {
    const response = await apiClient.get('/investments', { params });
    return response.data.data;
  }
  
  async createBuyInvestment(data) {
    const response = await apiClient.post('/investments/buy', data);
    return response.data.data;
  }
  
  async createSellInvestment(data) {
    const response = await apiClient.post('/investments/sell', data);
    return response.data.data;
  }
  
  async updateInvestment(data) {
    const response = await apiClient.post('/investments/update', data);
    return response.data.data;
  }
  
  async deleteInvestment(id) {
    const response = await apiClient.post('/investments/delete', { id });
    return response.data.data;
  }
  
  // 포트폴리오 관련
  async getPortfolioSummary() {
    const response = await apiClient.get('/portfolio/summary');
    return response.data.data;
  }
  
  async updatePortfolioSettings(data) {
    const response = await apiClient.post('/portfolio/settings/update', data);
    return response.data.data;
  }
  
  // 캘린더 관련
  async getCalendarData(year, month) {
    const response = await apiClient.get('/calendar', {
      params: { year, month }
    });
    return response.data.data;
  }
}

// 사용 예시
const apiService = new InvestmentApiService();

// 로그인
const user = await apiService.login({
  username: 'user123',
  password: 'password123'
});

// 투자 기록 조회
const investments = await apiService.getInvestments({
  page: 0,
  size: 20,
  type: 'buy'
});

// 투자 기록 수정
const updatedInvestment = await apiService.updateInvestment({
  id: 1,
  investmentRatio: 16.0,
  quantity: 12,
  pricePerShare: 76000,
  buyReason: "수정된 매수 이유"
});

// 투자 기록 삭제
await apiService.deleteInvestment(1);

// 포트폴리오 요약 조회
const summary = await apiService.getPortfolioSummary();

// 포트폴리오 설정 수정
await apiService.updatePortfolioSettings({
  totalSeed: 15000000,
  currency: "KRW"
});
```

### 3. React Hook 예시

```javascript
import { useState, useEffect } from 'react';
import { apiService } from '../services/apiService';

// 포트폴리오 요약 Hook
export const usePortfolioSummary = () => {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  useEffect(() => {
    const fetchSummary = async () => {
      try {
        setLoading(true);
        const data = await apiService.getPortfolioSummary();
        setSummary(data);
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    };
    
    fetchSummary();
  }, []);
  
  return { summary, loading, error };
};

// 투자 기록 Hook
export const useInvestments = (params = {}) => {
  const [investments, setInvestments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  useEffect(() => {
    const fetchInvestments = async () => {
      try {
        setLoading(true);
        const data = await apiService.getInvestments(params);
        setInvestments(data.content);
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    };
    
    fetchInvestments();
  }, [params]);
  
  return { investments, loading, error };
};
```

### 4. 에러 처리

```javascript
// 전역 에러 처리
const handleApiError = (error) => {
  if (error.response) {
    const { status, data } = error.response;
    
    switch (status) {
      case 400:
        console.error('잘못된 요청:', data.error.message);
        break;
      case 401:
        console.error('인증 실패:', data.error.message);
        // 로그인 페이지로 리다이렉트
        break;
      case 403:
        console.error('권한 없음:', data.error.message);
        break;
      case 404:
        console.error('리소스를 찾을 수 없음:', data.error.message);
        break;
      case 500:
        console.error('서버 오류:', data.error.message);
        break;
      default:
        console.error('알 수 없는 오류:', data.error.message);
    }
  } else if (error.request) {
    console.error('네트워크 오류:', error.message);
  } else {
    console.error('요청 설정 오류:', error.message);
  }
};
```

### 5. JWT 토큰 자동 갱신 인터셉터

```javascript
// Axios 인터셉터 설정
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    if (error.response.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const response = await axios.post('/api/v1/auth/refresh', {}, {
          headers: { Authorization: `Bearer ${refreshToken}` }
        });
        
        const { accessToken } = response.data.data;
        localStorage.setItem('accessToken', accessToken);
        
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        // 리프레시 토큰도 만료된 경우 로그아웃
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    
    return Promise.reject(error);
  }
);
```

### 6. 실제 구현된 API 엔드포인트 (요약)

#### **인증 API (`AuthController`)**
- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- WebAuthn: `POST .../webauthn/register/start|finish`, `POST .../webauthn/login/start|finish`, `GET .../webauthn/credentials`, 패스키 추가·이름 변경·삭제: `POST .../credentials/add/start|finish`, `POST .../credentials/{id}/name/update`, `POST .../credentials/{id}/delete`
- 프로필·비밀번호: `POST /api/v1/auth/user/profile/update`, `POST /api/v1/auth/user/password/update`, `POST /api/v1/auth/user/password/defer`

#### **투자 기록 (`InvestmentController`)**
- `GET /api/v1/investments`, `GET /api/v1/investments/{id}`
- `POST /api/v1/investments/buy`, `sell`, `update`, `delete`

#### **포트폴리오 (`PortfolioController`)**
- `GET /api/v1/portfolio/summary`, `GET /api/v1/portfolio/settings`, `POST /api/v1/portfolio/settings/update`

#### **캘린더 (`CalendarController`)**
- `GET /api/v1/calendar`, `GET /api/v1/calendar/{date}/records`, `GET /api/v1/calendar/{date}/portfolio`

#### **자산 (`AssetController`)**
- 위 [자산관리·기타 API 요약](#자산관리기타-api-요약) 절 참고 (고정 수입·지출은 `POST .../{id}/update`, `POST .../{id}/delete`)

#### **기타**
- `StockDataController` `/api/v1/stock-data/*`, `ContactController` `POST /api/v1/contact/inquiries`, `QueueController` `/api/v1/queue/*`, `AdminController` `POST /api/v1/admin/encrypt` 등 — 전체는 Swagger 참고

**커뮤니티 API**: 미구현 (별도 컨트롤러 없음).

### 7. Spring Security 설정 정보

#### **보안 설정 (SecurityConfig)**
- **CSRF**: 비활성화 (JWT 사용)
- **세션**: STATELESS
- **CORS**: 허용 origin은 `CORS_ALLOWED_ORIGINS` / `app.cors.allowed-origins`, 메서드는 GET·POST·OPTIONS·HEAD
- **공개 엔드포인트**: `/api/v1/auth/**`, Swagger UI
- **보호 엔드포인트**: 인증된 사용자만 접근

#### **권한 관리**
- **USER**: 일반 사용자 권한
- **ADMIN**: 관리자 권한 (관리자 전용 API 접근)

### 8. 데이터베이스 스키마 정보

#### **주요 테이블**
- **users**: 사용자 정보 (role, status, login_attempts, email_verified, phone_verified 등)
- **investment_records**: 투자 기록 (type, current_price, unrealized_profit, is_deleted 등)
- **portfolio_settings**: 포트폴리오 설정
- **user_sessions**: 사용자 세션 관리 (session_id, access_token, last_activity_at 등)
- **user_levels**: 사용자 레벨 및 포인트 (level, experience, total_points, current_points 등)
- **point_history**: 포인트 히스토리 (type, balance_after, description 등)
- **notifications**: 알림 (type, title, message, is_read, read_at 등)
- **community_posts**, **community_comments**: 스키마에 존재할 수 있으나 **현재 API 미구현**

#### **보안 기능**
- **AES-256 암호화**: 민감 정보 보호
- **bcrypt 해싱**: 비밀번호 보안
- **자동 백업**: 데이터 무결성 보장

---

이 명세서를 참조하여 프론트엔드에서 투자일지 API를 효과적으로 연동할 수 있습니다! 🚀 