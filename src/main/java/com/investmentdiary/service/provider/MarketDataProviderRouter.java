package com.investmentdiary.service.provider;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class MarketDataProviderRouter {

    private final List<MarketDataProvider> providers;

    public MarketDataProviderRouter(List<MarketDataProvider> providers) {
        this.providers = providers;
    }

    public Map<String, Object> getStockPrice(String ticker) {
        return call(p -> p.getStockPrice(ticker));
    }

    public Map<String, Object> getStockPriceHistory(String ticker, LocalDate startDate, LocalDate endDate) {
        return call(p -> p.getStockPriceHistory(ticker, startDate, endDate));
    }

    public Map<String, Object> getDividendInfo(String ticker) {
        return call(p -> p.getDividendInfo(ticker));
    }

    public Map<String, Object> getExchangeRate(String baseCurrency, String quoteCurrency) {
        return call(p -> p.getExchangeRate(baseCurrency, quoteCurrency));
    }

    private Map<String, Object> call(ProviderCall call) {
        for (MarketDataProvider provider : providers) {
            Map<String, Object> result = call.apply(provider);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface ProviderCall {
        Map<String, Object> apply(MarketDataProvider provider);
    }
}
