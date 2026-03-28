package com.investmentdiary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 시장 데이터 조회 서비스
 * Python 프록시를 거치지 않고 Java에서 외부 데이터 소스를 직접 호출한다.
 */
@Slf4j
@Service
public class YahooFinanceService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String alphaVantageApiKey;
    private final String yahooUserAgent;
    private final int yahooMinIntervalMs;
    private final int yahooMaxRetriesOn429;

    private final Object yahooThrottleLock = new Object();
    private volatile long lastYahooCallEndMillis = 0L;
    
    @Autowired
    public YahooFinanceService(
            RestTemplate restTemplate, 
            ObjectMapper objectMapper,
            @Value("${ALPHA_VANTAGE_API_KEY:}") String alphaVantageApiKey,
            @Value("${market-data.yahoo.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36}") String yahooUserAgent,
            @Value("${market-data.yahoo.min-interval-ms:550}") int yahooMinIntervalMs,
            @Value("${market-data.yahoo.max-retries-on-429:4}") int yahooMaxRetriesOn429) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.alphaVantageApiKey = alphaVantageApiKey;
        this.yahooUserAgent = yahooUserAgent;
        this.yahooMinIntervalMs = Math.max(0, yahooMinIntervalMs);
        this.yahooMaxRetriesOn429 = Math.max(1, yahooMaxRetriesOn429);
    }
    
    /**
     * 주식 종가 조회
     * @param ticker Yahoo Finance 티커 (예: 005930.KS, AAPL)
     * @return 종가 정보 (날짜, 종가, 시가, 고가, 저가, 거래량)
     */
    public Map<String, Object> getStockPrice(String ticker) {
        try {
            // 최근 7일 범위에서 마지막 거래일 1건 조회
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://query1.finance.yahoo.com/v8/finance/chart/{ticker}")
                    .queryParam("interval", "1d")
                    .queryParam("range", "7d")
                    .buildAndExpand(ticker)
                    .toUriString();

            ResponseEntity<String> response = yahooHttpGet(url);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultNode = root.path("chart").path("result");
            if (!resultNode.isArray() || resultNode.isEmpty()) return null;

            JsonNode node = resultNode.get(0);
            JsonNode timestamps = node.path("timestamp");
            JsonNode quote = node.path("indicators").path("quote");
            if (!quote.isArray() || quote.isEmpty()) return null;
            JsonNode quote0 = quote.get(0);
            JsonNode opens = quote0.path("open");
            JsonNode highs = quote0.path("high");
            JsonNode lows = quote0.path("low");
            JsonNode closes = quote0.path("close");
            JsonNode volumes = quote0.path("volume");
            if (!timestamps.isArray() || timestamps.isEmpty()) return null;

            int idx = -1;
            for (int i = timestamps.size() - 1; i >= 0; i--) {
                if (closes.has(i) && !closes.get(i).isNull()) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) return null;

            long epoch = timestamps.get(idx).asLong();
            LocalDate priceDate = Instant.ofEpochSecond(epoch).atZone(ZoneId.of("UTC")).toLocalDate();
            JsonNode meta = node.path("meta");
            String currency = meta.path("currency").asText("KRW");
            String stockName = meta.path("shortName").asText(ticker);

            Map<String, Object> priceData = new HashMap<>();
            priceData.put("ticker", ticker);
            priceData.put("stockName", stockName);
            priceData.put("priceDate", priceDate);
            priceData.put("closePrice", toBigDecimal(closes.get(idx)));
            priceData.put("openPrice", toBigDecimal(opens.get(idx)));
            priceData.put("highPrice", toBigDecimal(highs.get(idx)));
            priceData.put("lowPrice", toBigDecimal(lows.get(idx)));
            priceData.put("volume", volumes.has(idx) && !volumes.get(idx).isNull() ? volumes.get(idx).asLong() : null);
            priceData.put("currency", currency);
            return priceData;
        } catch (RestClientException e) {
            log.error("Yahoo 직접 호출 실패 (종가): ticker={}, error={}", ticker, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("종가 데이터 처리 실패: ticker={}, error={}", ticker, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 배당 정보 조회
     * @param ticker Yahoo Finance 티커
     * @return 배당 정보 (배당수익률, 연간 배당금 등)
     */
    public Map<String, Object> getDividendInfo(String ticker) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://query1.finance.yahoo.com/v10/finance/quoteSummary/{ticker}")
                    .queryParam("modules", "summaryDetail")
                    .buildAndExpand(ticker)
                    .toUriString();
            ResponseEntity<String> response = yahooHttpGet(url);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode result = root.path("quoteSummary").path("result");
            if (!result.isArray() || result.isEmpty()) return null;
            JsonNode detail = result.get(0).path("summaryDetail");
            if (detail.isMissingNode()) return null;

            Map<String, Object> dividendData = new HashMap<>();
            BigDecimal dividendRate = toBigDecimal(detail.path("dividendRate").path("raw"));
            BigDecimal dividendYield = toBigDecimal(detail.path("dividendYield").path("raw"));
            JsonNode exDividendDate = detail.path("exDividendDate").path("raw");
            if (dividendRate != null) {
                dividendData.put("annualDividend", dividendRate);
                dividendData.put("dividendPerShare", dividendRate);
            }
            if (dividendYield != null) {
                dividendData.put("dividendYield", dividendYield.multiply(BigDecimal.valueOf(100)));
            }
            if (!exDividendDate.isMissingNode() && !exDividendDate.isNull()) {
                LocalDate date = Instant.ofEpochSecond(exDividendDate.asLong()).atZone(ZoneId.of("UTC")).toLocalDate();
                dividendData.put("dividendDate", date.toString());
            }
            return dividendData.isEmpty() ? null : dividendData;
        } catch (RestClientException e) {
            log.error("Yahoo 직접 호출 실패 (배당): {}", ticker, e);
            return null;
        } catch (Exception e) {
            log.error("배당 데이터 처리 실패: {}", ticker, e);
            return null;
        }
    }
    
    /**
     * 환율 조회
     * @param baseCurrency 기준 통화 (예: USD)
     * @param quoteCurrency 상대 통화 (예: KRW)
     * @return 환율 정보
     */
    public Map<String, Object> getExchangeRate(String baseCurrency, String quoteCurrency) {
        try {
            // 무료 우선: exchangerate.host
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://api.exchangerate.host/convert")
                    .queryParam("from", baseCurrency)
                    .queryParam("to", quoteCurrency)
                    .queryParam("amount", "1")
                    .toUriString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode result = root.path("result");
                JsonNode date = root.path("date");
                if (!result.isMissingNode() && !result.isNull()) {
                    Map<String, Object> rateData = new HashMap<>();
                    rateData.put("rateDate", date.isMissingNode() || date.isNull() ? LocalDate.now() : LocalDate.parse(date.asText()));
                    rateData.put("rate", toBigDecimal(result));
                    return rateData;
                }
            }

            // 기존 API 키 활용 fallback (Alpha Vantage)
            if (alphaVantageApiKey != null && !alphaVantageApiKey.isBlank()) {
                String avUrl = UriComponentsBuilder
                        .fromHttpUrl("https://www.alphavantage.co/query")
                        .queryParam("function", "CURRENCY_EXCHANGE_RATE")
                        .queryParam("from_currency", baseCurrency)
                        .queryParam("to_currency", quoteCurrency)
                        .queryParam("apikey", alphaVantageApiKey)
                        .toUriString();
                ResponseEntity<String> avResponse = restTemplate.getForEntity(avUrl, String.class);
                if (avResponse.getStatusCode().is2xxSuccessful() && avResponse.getBody() != null) {
                    JsonNode avRoot = objectMapper.readTree(avResponse.getBody());
                    JsonNode exchangeNode = avRoot.path("Realtime Currency Exchange Rate");
                    JsonNode rateNode = exchangeNode.path("5. Exchange Rate");
                    JsonNode dateNode = exchangeNode.path("6. Last Refreshed");
                    if (!rateNode.isMissingNode() && !rateNode.isNull()) {
                        Map<String, Object> rateData = new HashMap<>();
                        rateData.put("rateDate", LocalDate.parse(dateNode.asText().substring(0, 10)));
                        rateData.put("rate", new BigDecimal(rateNode.asText()));
                        return rateData;
                    }
                }
            }
            return null;
        } catch (RestClientException e) {
            log.error("환율 API 호출 실패: {}/{}", baseCurrency, quoteCurrency, e);
            return null;
        } catch (Exception e) {
            log.error("환율 데이터 처리 실패: {}/{}", baseCurrency, quoteCurrency, e);
            return null;
        }
    }

    /**
     * 과거 종가 히스토리 조회
     */
    public Map<String, Object> getStockPriceHistory(String ticker, LocalDate startDate, LocalDate endDate) {
        try {
            long startEpoch = startDate.atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
            long endEpoch = endDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://query1.finance.yahoo.com/v8/finance/chart/{ticker}")
                    .queryParam("interval", "1d")
                    .queryParam("period1", String.valueOf(startEpoch))
                    .queryParam("period2", String.valueOf(endEpoch))
                    .buildAndExpand(ticker)
                    .toUriString();

            ResponseEntity<String> response = yahooHttpGet(url);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultNode = root.path("chart").path("result");
            if (!resultNode.isArray() || resultNode.isEmpty()) return null;
            JsonNode node = resultNode.get(0);
            JsonNode timestamps = node.path("timestamp");
            JsonNode quote = node.path("indicators").path("quote");
            if (!quote.isArray() || quote.isEmpty() || !timestamps.isArray()) return null;
            JsonNode quote0 = quote.get(0);
            JsonNode opens = quote0.path("open");
            JsonNode highs = quote0.path("high");
            JsonNode lows = quote0.path("low");
            JsonNode closes = quote0.path("close");
            JsonNode volumes = quote0.path("volume");
            String currency = node.path("meta").path("currency").asText("KRW");

            List<Map<String, Object>> prices = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (!closes.has(i) || closes.get(i).isNull()) continue;
                LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneId.of("UTC")).toLocalDate();
                Map<String, Object> row = new HashMap<>();
                row.put("priceDate", date.toString());
                row.put("openPrice", toBigDecimal(opens.get(i)));
                row.put("highPrice", toBigDecimal(highs.get(i)));
                row.put("lowPrice", toBigDecimal(lows.get(i)));
                row.put("closePrice", toBigDecimal(closes.get(i)));
                row.put("volume", volumes.has(i) && !volumes.get(i).isNull() ? volumes.get(i).asLong() : null);
                row.put("currency", currency);
                prices.add(row);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("ticker", ticker);
            result.put("prices", prices);
            return result;
        } catch (Exception e) {
            log.error("히스토리 조회 실패: ticker={}, error={}", ticker, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 외부 API를 호출하고 결과를 Map으로 반환
     * @param url 호출할 URL
     * @return 응답 데이터 (Map)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> callExternalApi(String url) {
        try {
            log.debug("외부 API 호출: {}", url);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (Map<String, Object>) response.getBody();
            } else {
                log.warn("외부 API 응답 실패: url={}, status={}", url, response.getStatusCode());
                return null;
            }
        } catch (RestClientException e) {
            log.error("외부 API 호출 실패: url={}, error={}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("외부 API 처리 실패: url={}, error={}", url, e.getMessage(), e);
            return null;
        }
    }

    private BigDecimal toBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Yahoo chart/quote API 호출: 호출 간격 제한, User-Agent, 429 시 백오프·query1/query2 전환.
     */
    private ResponseEntity<String> yahooHttpGet(String initialUrl) {
        String url = initialUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, yahooUserAgent);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (int attempt = 0; attempt < yahooMaxRetriesOn429; attempt++) {
            throttleBeforeYahoo();
            try {
                ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                markYahooCallEnd();
                return res;
            } catch (HttpClientErrorException e) {
                markYahooCallEnd();
                if (e.getStatusCode().value() != 429) {
                    log.error("Yahoo HTTP 오류: status={}, url={}", e.getStatusCode().value(), url);
                    return null;
                }
                if (attempt >= yahooMaxRetriesOn429 - 1) {
                    log.error("Yahoo 429 반복으로 포기: {}", initialUrl);
                    return null;
                }
                long waitMs = parseRetryAfterMs(e);
                if (waitMs < 0) {
                    waitMs = Math.min(60_000L, 2000L * (attempt + 1));
                }
                log.warn("Yahoo 429, {}ms 대기 후 재시도 ({}/{}): {}", waitMs, attempt + 1, yahooMaxRetriesOn429, url);
                sleepQuiet(waitMs);
                url = alternateYahooHost(url);
            } catch (RestClientException e) {
                markYahooCallEnd();
                log.error("Yahoo 호출 실패: url={}, error={}", url, e.getMessage());
                return null;
            }
        }
        return null;
    }

    private void throttleBeforeYahoo() {
        if (yahooMinIntervalMs <= 0) {
            return;
        }
        synchronized (yahooThrottleLock) {
            long now = System.currentTimeMillis();
            long nextAllowed = lastYahooCallEndMillis + yahooMinIntervalMs;
            if (now < nextAllowed) {
                sleepQuiet(nextAllowed - now);
            }
        }
    }

    private void markYahooCallEnd() {
        synchronized (yahooThrottleLock) {
            lastYahooCallEndMillis = System.currentTimeMillis();
        }
    }

    private static String alternateYahooHost(String url) {
        if (url.contains("query1.finance.yahoo.com")) {
            return url.replace("query1.finance.yahoo.com", "query2.finance.yahoo.com");
        }
        if (url.contains("query2.finance.yahoo.com")) {
            return url.replace("query2.finance.yahoo.com", "query1.finance.yahoo.com");
        }
        return url;
    }

    private static long parseRetryAfterMs(HttpClientErrorException e) {
        if (e.getResponseHeaders() == null) {
            return -1;
        }
        String ra = e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (ra == null || ra.isBlank()) {
            return -1;
        }
        try {
            return Long.parseLong(ra.trim()) * 1000L;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static void sleepQuiet(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

