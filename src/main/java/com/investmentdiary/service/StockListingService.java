package com.investmentdiary.service;

import com.investmentdiary.entity.StockListing;
import com.investmentdiary.repository.StockListingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 종목 리스트 관리 서비스
 * - DB에 종목 리스트를 캐시하여 KRX 서버 장애 시에도 종목 검색 가능
 * - 스케줄러로 외부 무료 소스/API를 직접 호출하여 업데이트
 */
@Slf4j
@Service
public class StockListingService {

    private final StockListingRepository stockListingRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String alphaVantageApiKey;

    @Autowired
    public StockListingService(
            StockListingRepository stockListingRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${ALPHA_VANTAGE_API_KEY:}") String alphaVantageApiKey) {
        this.stockListingRepository = stockListingRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.alphaVantageApiKey = alphaVantageApiKey;
    }

    /**
     * 유럽 국가 코드 목록
     */
    private static final List<String> EU_COUNTRIES = Arrays.asList(
            "GB", "DE", "FR", "IT", "ES", "NL", "CH", "BE", "AT", "SE", "NO", "DK", "FI", "IE", "PT"
    );

    /**
     * 종목 검색 (DB에서 조회)
     *
     * @param query   검색어 (종목명 또는 종목코드)
     * @param country 국가 (KR, US, JP, EU, null=전체)
     * @param limit   최대 결과 수
     * @return 검색 결과 리스트
     */
    public List<Map<String, Object>> searchStocks(String query, String country, int limit) {
        List<StockListing> listings;
        
        // EU 검색 시 유럽 국가 목록으로 검색
        if ("EU".equalsIgnoreCase(country)) {
            listings = new ArrayList<>();
            for (String euCountry : EU_COUNTRIES) {
                List<StockListing> countryListings = stockListingRepository.searchByQueryAndCountry(query, euCountry);
                listings.addAll(countryListings);
            }
            // 관련도 순으로 정렬 (정확 일치 > 시작 일치 > 포함 일치)
            listings.sort((a, b) -> {
                boolean aExactCode = a.getStockCode().equals(query);
                boolean bExactCode = b.getStockCode().equals(query);
                if (aExactCode != bExactCode) return aExactCode ? -1 : 1;
                
                boolean aStartsCode = a.getStockCode().startsWith(query);
                boolean bStartsCode = b.getStockCode().startsWith(query);
                if (aStartsCode != bStartsCode) return aStartsCode ? -1 : 1;
                
                boolean aExactName = a.getStockName().equals(query);
                boolean bExactName = b.getStockName().equals(query);
                if (aExactName != bExactName) return aExactName ? -1 : 1;
                
                boolean aStartsName = a.getStockName().startsWith(query);
                boolean bStartsName = b.getStockName().startsWith(query);
                if (aStartsName != bStartsName) return aStartsName ? -1 : 1;
                
                return a.getStockName().compareTo(b.getStockName());
            });
        } else {
            listings = stockListingRepository.searchByQueryAndCountry(query, country);
        }

        return listings.stream()
                .limit(limit)
                .map(listing -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("stockCode", listing.getStockCode());
                    result.put("stockName", listing.getStockName());
                    result.put("market", listing.getMarket());
                    result.put("ticker", listing.getTicker());
                    result.put("country", listing.getCountry());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 외부 API를 통한 실시간 종목 검색 (DB에 없는 종목 보완)
     *
     * @param query   검색어 (종목명 또는 종목코드)
     * @param market  시장 필터 (EU 등)
     * @param limit   최대 결과 수
     * @return 검색 결과 리스트
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchStocksFromPython(String query, String market, int limit) {
        try {
            return searchSymbolsFromAlphaVantage(query, market, limit);
        } catch (Exception e) {
            log.error("외부 API 종목 검색 실패: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * DB에 종목 데이터가 있는지 확인
     */
    public boolean hasListings(String country) {
        return stockListingRepository.countByCountryAndIsActive(country, true) > 0;
    }

    /**
     * 유럽 종목을 DB에 저장 (검색 결과 기반)
     * 
     * @param stockData 종목 데이터 (stockCode, stockName, market, ticker, country 포함)
     * @return 저장된 StockListing 엔티티
     */
    @Transactional
    public StockListing saveEuStockListing(Map<String, Object> stockData) {
        String stockCode = (String) stockData.get("stockCode");
        String stockName = (String) stockData.get("stockName");
        String market = (String) stockData.getOrDefault("market", "EU");
        String ticker = (String) stockData.get("ticker");
        String country = (String) stockData.getOrDefault("country", "EU");

        if (stockCode == null || stockCode.isBlank()) {
            log.warn("유럽 종목 저장 실패: stockCode가 없음");
            return null;
        }

        // 기존 종목 확인
        Optional<StockListing> existing = stockListingRepository.findByStockCodeAndCountry(stockCode, country);
        
        if (existing.isPresent()) {
            // 기존 종목 업데이트
            StockListing listing = existing.get();
            boolean changed = false;
            
            if (stockName != null && !stockName.equals(listing.getStockName())) {
                listing.setStockName(stockName);
                changed = true;
            }
            if (market != null && !market.equals(listing.getMarket())) {
                listing.setMarket(market);
                changed = true;
            }
            if (ticker != null && !ticker.equals(listing.getTicker())) {
                listing.setTicker(ticker);
                changed = true;
            }
            if (!listing.getIsActive()) {
                listing.setIsActive(true);
                changed = true;
            }
            
            if (changed) {
                listing = stockListingRepository.save(listing);
                log.debug("유럽 종목 업데이트: {} ({})", stockName, stockCode);
            }
            return listing;
        } else {
            // 신규 종목 추가
            StockListing newListing = StockListing.builder()
                    .stockCode(stockCode)
                    .stockName(stockName != null ? stockName : stockCode)
                    .market(market)
                    .ticker(ticker)
                    .country(country)
                    .isActive(true)
                    .build();
            
            newListing = stockListingRepository.save(newListing);
            log.debug("유럽 종목 신규 저장: {} ({})", stockName, stockCode);
            return newListing;
        }
    }

    /**
     * 유럽 종목 리스트를 DB에 저장 (검색 결과 기반)
     * 
     * @param stockDataList 종목 데이터 리스트
     * @return 저장된 종목 수
     */
    @Transactional
    public int saveEuStockListings(List<Map<String, Object>> stockDataList) {
        if (stockDataList == null || stockDataList.isEmpty()) {
            return 0;
        }

        int saved = 0;
        for (Map<String, Object> stockData : stockDataList) {
            StockListing listing = saveEuStockListing(stockData);
            if (listing != null) {
                saved++;
            }
        }
        
        log.info("유럽 종목 DB 저장 완료: {}건", saved);
        return saved;
    }

    /**
     * 매일 오전 6시에 KR 종목 리스트 업데이트 (장 시작 전)
     */
    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "Asia/Seoul")
    public void scheduledUpdateKrListings() {
        log.info("[스케줄러] KR 종목 리스트 업데이트 시작");
        try {
            int count = syncListingsFromPython("KR");
            log.info("[스케줄러] KR 종목 리스트 업데이트 완료: {}개", count);
        } catch (Exception e) {
            log.error("[스케줄러] KR 종목 리스트 업데이트 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매일 오전 6시에 US 종목 리스트 업데이트 (한국 장 시작 전)
     */
    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "Asia/Seoul")
    public void scheduledUpdateUsListings() {
        log.info("[스케줄러] US 종목 리스트 업데이트 시작");
        try {
            int count = syncListingsFromPython("US");
            log.info("[스케줄러] US 종목 리스트 업데이트 완료: {}개", count);
        } catch (Exception e) {
            log.error("[스케줄러] US 종목 리스트 업데이트 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매일 오전 6시에 JP 종목 리스트 업데이트 (한국 장 시작 전)
     */
    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "Asia/Seoul")
    public void scheduledUpdateJpListings() {
        log.info("[스케줄러] JP 종목 리스트 업데이트 시작");
        try {
            int count = syncListingsFromPython("JP");
            log.info("[스케줄러] JP 종목 리스트 업데이트 완료: {}개", count);
        } catch (Exception e) {
            log.error("[스케줄러] JP 종목 리스트 업데이트 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 외부 API에서 종목 리스트를 가져와 DB에 동기화
     *
     * @param country 국가 코드 (KR, US, JP)
     * @return 동기화된 종목 수
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int syncListingsFromPython(String country) {
        try {
            List<Map<String, Object>> listings = fetchListingsByCountry(country);

            if (listings == null || listings.isEmpty()) {
                long existingCount = stockListingRepository.countByCountryAndIsActive(country, true);
                log.warn("외부 소스에서 종목 데이터 없음: country={}, 기존 DB 데이터 유지(count={})", country, existingCount);
                return (int) existingCount;
            }

            log.info("외부 소스에서 {}개 종목 수신, DB 동기화 시작(country={})", listings.size(), country);

            // 기존 종목 코드 맵 (stock_code+country -> entity)
            Map<String, StockListing> existingMap = stockListingRepository
                    .findByCountryAndIsActive(country, true)
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getStockCode() + "_" + s.getCountry(),
                            s -> s,
                            (a, b) -> a
                    ));

            // 새로 받은 종목 코드 셋
            Set<String> newCodes = new HashSet<>();
            int created = 0;
            int updated = 0;

            for (Map<String, Object> item : listings) {
                String stockCode = (String) item.get("stockCode");
                String stockName = (String) item.get("stockName");
                String itemMarket = (String) item.get("market");
                String ticker = (String) item.get("ticker");
                String itemCountry = (String) item.getOrDefault("country", country);

                if (stockCode == null || stockCode.isBlank()) continue;

                String key = stockCode + "_" + itemCountry;
                newCodes.add(key);

                StockListing existing = existingMap.get(key);
                if (existing != null) {
                    // 기존 종목 업데이트 (이름/시장/티커 변경 시)
                    boolean changed = false;
                    if (!Objects.equals(existing.getStockName(), stockName)) {
                        existing.setStockName(stockName);
                        changed = true;
                    }
                    if (!Objects.equals(existing.getMarket(), itemMarket)) {
                        existing.setMarket(itemMarket);
                        changed = true;
                    }
                    if (!Objects.equals(existing.getTicker(), ticker)) {
                        existing.setTicker(ticker);
                        changed = true;
                    }
                    if (!existing.getIsActive()) {
                        existing.setIsActive(true);
                        changed = true;
                    }
                    if (changed) {
                        stockListingRepository.save(existing);
                        updated++;
                    }
                } else {
                    // 신규 종목 추가
                    StockListing newListing = StockListing.builder()
                            .stockCode(stockCode)
                            .stockName(stockName)
                            .market(itemMarket)
                            .ticker(ticker)
                            .country(itemCountry)
                            .isActive(true)
                            .build();
                    stockListingRepository.save(newListing);
                    created++;
                }
            }

            // 새 리스트에 없는 기존 종목은 비활성화 (상장폐지 등)
            int deactivated = 0;
            for (Map.Entry<String, StockListing> entry : existingMap.entrySet()) {
                if (!newCodes.contains(entry.getKey())) {
                    StockListing listing = entry.getValue();
                    listing.setIsActive(false);
                    stockListingRepository.save(listing);
                    deactivated++;
                }
            }

            log.info("종목 리스트 동기화 완료: 신규={}, 업데이트={}, 비활성화={}, 전체={}",
                    created, updated, deactivated, listings.size());

            return listings.size();

        } catch (Exception e) {
            log.error("외부 소스 종목 리스트 동기화 실패: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 애플리케이션 시작 시 DB에 종목 데이터가 없으면 초기 로딩
     */
    @jakarta.annotation.PostConstruct
    public void initializeListingsIfEmpty() {
        try {
            long krCount = stockListingRepository.countByCountryAndIsActive("KR", true);
            long usCount = stockListingRepository.countByCountryAndIsActive("US", true);
            long jpCount = stockListingRepository.countByCountryAndIsActive("JP", true);
            
            if (krCount == 0 || usCount == 0 || jpCount == 0) {
                log.info("DB에 종목 데이터 확인: KR={}개, US={}개, JP={}개", krCount, usCount, jpCount);
                // 비동기로 초기 로딩 (애플리케이션 시작 속도에 영향 없도록)
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        
                        // KR 종목 로딩
                        if (krCount == 0) {
                            log.info("KR 종목 초기 로딩 시작...");
                            int count = syncListingsFromPython("KR");
                            if (count > 0) {
                                log.info("KR 종목 초기 로딩 완료: {}개", count);
                            } else {
                                log.warn("KR 종목 초기 로딩 실패 - 외부 데이터 소스/키 설정을 확인하세요");
                            }
                        } else {
                            log.info("KR 종목 데이터 이미 존재: {}개", krCount);
                        }
                        
                        // US 종목 로딩
                        if (usCount == 0) {
                            log.info("US 종목 초기 로딩 시작...");
                            int count = syncListingsFromPython("US");
                            if (count > 0) {
                                log.info("US 종목 초기 로딩 완료: {}개", count);
                            } else {
                                log.warn("US 종목 초기 로딩 실패 - 외부 데이터 소스/키 설정을 확인하세요");
                            }
                        } else {
                            log.info("US 종목 데이터 이미 존재: {}개", usCount);
                        }
                        
                        // JP 종목 로딩
                        if (jpCount == 0) {
                            log.info("JP 종목 초기 로딩 시작...");
                            int count = syncListingsFromPython("JP");
                            if (count > 0) {
                                log.info("JP 종목 초기 로딩 완료: {}개", count);
                            } else {
                                log.warn("JP 종목 초기 로딩 실패 - 외부 데이터 소스/키 설정을 확인하세요");
                            }
                        } else {
                            log.info("JP 종목 데이터 이미 존재: {}개", jpCount);
                        }
                    } catch (Exception e) {
                        log.error("종목 초기 로딩 실패: {}", e.getMessage(), e);
                    }
                }, "stock-listing-init").start();
            } else {
                log.info("DB에 종목 데이터 존재: KR={}개, US={}개, JP={}개", krCount, usCount, jpCount);
            }
        } catch (Exception e) {
            log.warn("종목 리스트 초기화 확인 실패 (테이블이 아직 생성되지 않았을 수 있음): {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> fetchListingsByCountry(String country) {
        if ("US".equalsIgnoreCase(country)) {
            return fetchUsListingsFromAlphaVantage();
        }
        // 무료 우선 정책: KR/JP/EU는 기본적으로 기존 DB 캐시를 우선 사용한다.
        // (추후 국가별 소스 추가 시 이 메서드에서 확장)
        return Collections.emptyList();
    }

    private List<Map<String, Object>> searchSymbolsFromAlphaVantage(String query, String market, int limit) {
        if (alphaVantageApiKey == null || alphaVantageApiKey.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://www.alphavantage.co/query")
                    .queryParam("function", "SYMBOL_SEARCH")
                    .queryParam("keywords", query)
                    .queryParam("apikey", alphaVantageApiKey)
                    .toUriString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode matches = root.path("bestMatches");
            if (!matches.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode match : matches) {
                String symbol = match.path("1. symbol").asText();
                String name = match.path("2. name").asText(symbol);
                String region = match.path("4. region").asText("");
                String type = match.path("3. type").asText("");
                if (!"Equity".equalsIgnoreCase(type)) continue;

                String country = mapRegionToCountry(region);
                String itemMarket = "US".equals(country) ? "US" : ("JP".equals(country) ? "JP" : "EU");
                if (market != null && !market.isBlank() && !"ALL".equalsIgnoreCase(market)) {
                    if (!itemMarket.equalsIgnoreCase(market)) continue;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("stockCode", symbol);
                row.put("stockName", name);
                row.put("market", itemMarket);
                row.put("ticker", symbol);
                row.put("country", country);
                result.add(row);
                if (result.size() >= limit) break;
            }
            return result;
        } catch (Exception e) {
            log.warn("Alpha Vantage SYMBOL_SEARCH 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchUsListingsFromAlphaVantage() {
        if (alphaVantageApiKey == null || alphaVantageApiKey.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://www.alphavantage.co/query")
                    .queryParam("function", "LISTING_STATUS")
                    .queryParam("state", "active")
                    .queryParam("apikey", alphaVantageApiKey)
                    .toUriString();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Collections.emptyList();
            }
            String csv = response.getBody();
            String[] lines = csv.split("\\r?\\n");
            if (lines.length <= 1) return Collections.emptyList();

            List<Map<String, Object>> listings = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 4) continue;
                String symbol = cols.get(0);
                String name = cols.get(1);
                String exchange = cols.get(2);
                String assetType = cols.get(3);
                if (!"Stock".equalsIgnoreCase(assetType) || symbol.isBlank()) continue;

                Map<String, Object> row = new HashMap<>();
                row.put("stockCode", symbol);
                row.put("stockName", name == null || name.isBlank() ? symbol : name);
                row.put("market", exchange == null || exchange.isBlank() ? "US" : exchange);
                row.put("ticker", symbol);
                row.put("country", "US");
                listings.add(row);
            }
            return listings;
        } catch (Exception e) {
            log.warn("Alpha Vantage LISTING_STATUS 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String mapRegionToCountry(String region) {
        String normalized = region == null ? "" : region.trim().toLowerCase();
        if (normalized.contains("united states")) return "US";
        if (normalized.contains("japan")) return "JP";
        if (normalized.contains("korea")) return "KR";
        return "EU";
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
