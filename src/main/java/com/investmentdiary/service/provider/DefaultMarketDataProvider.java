package com.investmentdiary.service.provider;

import com.investmentdiary.service.YahooFinanceService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class DefaultMarketDataProvider implements MarketDataProvider {

    private final YahooFinanceService yahooFinanceService;

    public DefaultMarketDataProvider(YahooFinanceService yahooFinanceService) {
        this.yahooFinanceService = yahooFinanceService;
    }

    @Override
    public String providerId() {
        return "yahoo-direct";
    }

    @Override
    public Map<String, Object> getStockPrice(String ticker) {
        return yahooFinanceService.getStockPrice(ticker);
    }

    @Override
    public Map<String, Object> getStockPriceHistory(String ticker, LocalDate startDate, LocalDate endDate) {
        return yahooFinanceService.getStockPriceHistory(ticker, startDate, endDate);
    }

    @Override
    public Map<String, Object> getDividendInfo(String ticker) {
        return yahooFinanceService.getDividendInfo(ticker);
    }

    @Override
    public Map<String, Object> getExchangeRate(String baseCurrency, String quoteCurrency) {
        return yahooFinanceService.getExchangeRate(baseCurrency, quoteCurrency);
    }
}
