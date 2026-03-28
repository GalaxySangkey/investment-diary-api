package com.investmentdiary.service.provider;

import java.time.LocalDate;
import java.util.Map;

public interface MarketDataProvider {

    String providerId();

    Map<String, Object> getStockPrice(String ticker);

    Map<String, Object> getStockPriceHistory(String ticker, LocalDate startDate, LocalDate endDate);

    Map<String, Object> getDividendInfo(String ticker);

    Map<String, Object> getExchangeRate(String baseCurrency, String quoteCurrency);
}
