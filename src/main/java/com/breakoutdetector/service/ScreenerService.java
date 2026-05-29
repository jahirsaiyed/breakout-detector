package com.breakoutdetector.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScreenerService {

    @Autowired
    private WebClient webClient;

    public static class ScreenerResult {
        private String symbol;
        private String name;
        private double price;
        private double change;
        private double changePercent;
        private double volume;
        private double marketCap;
        private double peRatio;
        private double pbRatio;
        private double debtToEquity;
        private double roe;
        private double roa;
        private String sector;
        private String industry;
        private Map<String, Object> additionalData;

        // Constructors
        public ScreenerResult() {
            this.additionalData = new HashMap<>();
        }

        public ScreenerResult(String symbol, String name, double price) {
            this.symbol = symbol;
            this.name = name;
            this.price = price;
            this.additionalData = new HashMap<>();
        }

        // Getters and Setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public double getChange() { return change; }
        public void setChange(double change) { this.change = change; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
        public double getVolume() { return volume; }
        public void setVolume(double volume) { this.volume = volume; }
        public double getMarketCap() { return marketCap; }
        public void setMarketCap(double marketCap) { this.marketCap = marketCap; }
        public double getPeRatio() { return peRatio; }
        public void setPeRatio(double peRatio) { this.peRatio = peRatio; }
        public double getPbRatio() { return pbRatio; }
        public void setPbRatio(double pbRatio) { this.pbRatio = pbRatio; }
        public double getDebtToEquity() { return debtToEquity; }
        public void setDebtToEquity(double debtToEquity) { this.debtToEquity = debtToEquity; }
        public double getRoe() { return roe; }
        public void setRoe(double roe) { this.roe = roe; }
        public double getRoa() { return roa; }
        public void setRoa(double roa) { this.roa = roa; }
        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public Map<String, Object> getAdditionalData() { return additionalData; }
        public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }
    }

    public static class ScreenerQuery {
        private String name;
        private String description;
        private String query;
        private String source; // "stockanalysis" or "screener"
        private Map<String, Object> parameters;

        public ScreenerQuery() {
            this.parameters = new HashMap<>();
        }

        public ScreenerQuery(String name, String description, String query, String source) {
            this.name = name;
            this.description = description;
            this.query = query;
            this.source = source;
            this.parameters = new HashMap<>();
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }

    public List<ScreenerQuery> getPredefinedQueries() {
        List<ScreenerQuery> queries = new ArrayList<>();

        // StockAnalysis.com Queries
        queries.add(new ScreenerQuery(
            "Multibagger Momentum",
            "Stocks with strong momentum and volume breakouts",
            "price > 20 AND volume > 1000000 AND price_change_1d > 5 AND pe_ratio < 25",
            "stockanalysis"
        ));

        queries.add(new ScreenerQuery(
            "Breakout Candidates",
            "Stocks breaking out of resistance with high volume",
            "price > 50 AND volume > 2000000 AND price_change_1d > 3 AND market_cap > 1000000000",
            "stockanalysis"
        ));

        queries.add(new ScreenerQuery(
            "Volume Spikes",
            "Stocks with unusual volume activity",
            "volume > 3000000 AND volume_change_1d > 200 AND price_change_1d > 2",
            "stockanalysis"
        ));

        queries.add(new ScreenerQuery(
            "Strong Fundamentals",
            "Stocks with strong fundamentals and momentum",
            "pe_ratio < 20 AND roe > 15 AND debt_to_equity < 0.5 AND price_change_1d > 1",
            "stockanalysis"
        ));

        // Screener.in Queries
        queries.add(new ScreenerQuery(
            "NSE Multibagger",
            "NSE stocks with multibagger potential",
            "market_cap > 1000 AND price > 100 AND price_change_1d > 2 AND volume > 100000",
            "screener"
        ));

        queries.add(new ScreenerQuery(
            "NSE Breakout",
            "NSE stocks showing breakout patterns",
            "price > 50 AND volume > 500000 AND price_change_1d > 3 AND pe_ratio < 30",
            "screener"
        ));

        queries.add(new ScreenerQuery(
            "NSE Volume Leaders",
            "NSE stocks with highest volume activity",
            "volume > 1000000 AND price_change_1d > 1 AND market_cap > 500",
            "screener"
        ));

        queries.add(new ScreenerQuery(
            "NSE Strong Fundamentals",
            "NSE stocks with strong fundamentals",
            "pe_ratio < 25 AND roe > 12 AND debt_to_equity < 0.6 AND price_change_1d > 0",
            "screener"
        ));

        return queries;
    }

    public Map<String, Object> executeScreenerQuery(ScreenerQuery query) {
        Map<String, Object> result = new HashMap<>();
        List<ScreenerResult> stocks = new ArrayList<>();

        try {
            if ("stockanalysis".equals(query.getSource())) {
                stocks = executeStockAnalysisQuery(query);
            } else if ("screener".equals(query.getSource())) {
                stocks = executeScreenerInQuery(query);
            }

            result.put("success", true);
            result.put("query", query);
            result.put("stocks", stocks);
            result.put("totalResults", stocks.size());
            result.put("executionTime", System.currentTimeMillis());

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Failed to execute screener query: " + e.getMessage());
            result.put("query", query);
        }

        return result;
    }

    private List<ScreenerResult> executeStockAnalysisQuery(ScreenerQuery query) {
        // Simulate StockAnalysis.com API call
        // In a real implementation, you would make actual API calls
        List<ScreenerResult> results = new ArrayList<>();

        // Mock data for demonstration
        String[] mockSymbols = {"AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA", "META", "NFLX"};
        String[] mockNames = {"Apple Inc", "Microsoft Corp", "Alphabet Inc", "Amazon.com Inc", 
                             "Tesla Inc", "NVIDIA Corp", "Meta Platforms Inc", "Netflix Inc"};

        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            ScreenerResult stock = new ScreenerResult();
            stock.setSymbol(mockSymbols[i]);
            stock.setName(mockNames[i]);
            stock.setPrice(100 + random.nextDouble() * 900);
            stock.setChange(random.nextDouble() * 20 - 10);
            stock.setChangePercent(random.nextDouble() * 15 - 7.5);
            stock.setVolume(1000000 + random.nextDouble() * 9000000);
            stock.setMarketCap(10000000000L + random.nextDouble() * 90000000000L);
            stock.setPeRatio(10 + random.nextDouble() * 30);
            stock.setPbRatio(1 + random.nextDouble() * 5);
            stock.setDebtToEquity(random.nextDouble() * 2);
            stock.setRoe(5 + random.nextDouble() * 25);
            stock.setRoa(2 + random.nextDouble() * 15);
            stock.setSector("Technology");
            stock.setIndustry("Software");

            // Add additional data
            stock.getAdditionalData().put("beta", 1.0 + random.nextDouble());
            stock.getAdditionalData().put("dividend_yield", random.nextDouble() * 3);
            stock.getAdditionalData().put("52_week_high", stock.getPrice() * (1 + random.nextDouble() * 0.5));
            stock.getAdditionalData().put("52_week_low", stock.getPrice() * (1 - random.nextDouble() * 0.3));

            results.add(stock);
        }

        return results;
    }

    private List<ScreenerResult> executeScreenerInQuery(ScreenerQuery query) {
        // Simulate Screener.in API call
        // In a real implementation, you would make actual API calls
        List<ScreenerResult> results = new ArrayList<>();

        // Mock data for NSE stocks
        String[] mockSymbols = {"RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "HINDUNILVR", "ITC", "SBIN"};
        String[] mockNames = {"Reliance Industries", "Tata Consultancy Services", "HDFC Bank", 
                             "Infosys Ltd", "ICICI Bank", "Hindustan Unilever", "ITC Ltd", "State Bank of India"};

        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            ScreenerResult stock = new ScreenerResult();
            stock.setSymbol(mockSymbols[i]);
            stock.setName(mockNames[i]);
            stock.setPrice(500 + random.nextDouble() * 2000);
            stock.setChange(random.nextDouble() * 50 - 25);
            stock.setChangePercent(random.nextDouble() * 10 - 5);
            stock.setVolume(500000 + random.nextDouble() * 4500000);
            stock.setMarketCap(50000000000L + random.nextDouble() * 450000000000L);
            stock.setPeRatio(15 + random.nextDouble() * 25);
            stock.setPbRatio(2 + random.nextDouble() * 4);
            stock.setDebtToEquity(random.nextDouble() * 1.5);
            stock.setRoe(8 + random.nextDouble() * 20);
            stock.setRoa(3 + random.nextDouble() * 12);
            stock.setSector("Financial Services");
            stock.setIndustry("Banking");

            // Add additional data
            stock.getAdditionalData().put("face_value", 10.0);
            stock.getAdditionalData().put("book_value", stock.getPrice() * (0.5 + random.nextDouble() * 0.5));
            stock.getAdditionalData().put("promoter_holding", 30 + random.nextDouble() * 40);
            stock.getAdditionalData().put("fii_holding", 10 + random.nextDouble() * 30);

            results.add(stock);
        }

        return results;
    }

    public Map<String, Object> executeMultipleQueries(List<ScreenerQuery> queries) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> queryResults = new ArrayList<>();
        int totalStocks = 0;

        for (ScreenerQuery query : queries) {
            Map<String, Object> queryResult = executeScreenerQuery(query);
            queryResults.add(queryResult);
            
            if ((Boolean) queryResult.get("success")) {
                @SuppressWarnings("unchecked")
                List<ScreenerResult> stocks = (List<ScreenerResult>) queryResult.get("stocks");
                totalStocks += stocks.size();
            }
        }

        result.put("success", true);
        result.put("queries", queries);
        result.put("queryResults", queryResults);
        result.put("totalQueries", queries.size());
        result.put("totalStocks", totalStocks);
        result.put("executionTime", System.currentTimeMillis());

        return result;
    }

    public Map<String, Object> getScreenerDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        // Get predefined queries
        List<ScreenerQuery> queries = getPredefinedQueries();
        
        // Execute all queries
        Map<String, Object> results = executeMultipleQueries(queries);
        
        // Calculate statistics
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queryResults = (List<Map<String, Object>>) results.get("queryResults");
        
        int successfulQueries = 0;
        int totalStocks = 0;
        List<ScreenerResult> allStocks = new ArrayList<>();
        
        for (Map<String, Object> queryResult : queryResults) {
            if ((Boolean) queryResult.get("success")) {
                successfulQueries++;
                @SuppressWarnings("unchecked")
                List<ScreenerResult> stocks = (List<ScreenerResult>) queryResult.get("stocks");
                totalStocks += stocks.size();
                allStocks.addAll(stocks);
            }
        }

        // Calculate average metrics
        double avgPrice = allStocks.stream().mapToDouble(ScreenerResult::getPrice).average().orElse(0);
        double avgChangePercent = allStocks.stream().mapToDouble(ScreenerResult::getChangePercent).average().orElse(0);
        double avgPeRatio = allStocks.stream().mapToDouble(ScreenerResult::getPeRatio).average().orElse(0);
        double avgRoe = allStocks.stream().mapToDouble(ScreenerResult::getRoe).average().orElse(0);

        dashboard.put("totalQueries", queries.size());
        dashboard.put("successfulQueries", successfulQueries);
        dashboard.put("totalStocks", totalStocks);
        dashboard.put("avgPrice", avgPrice);
        dashboard.put("avgChangePercent", avgChangePercent);
        dashboard.put("avgPeRatio", avgPeRatio);
        dashboard.put("avgRoe", avgRoe);
        dashboard.put("queryResults", queryResults);
        dashboard.put("allStocks", allStocks);

        return dashboard;
    }

    public List<ScreenerResult> filterStocks(List<ScreenerResult> stocks, Map<String, Object> filters) {
        return stocks.stream()
            .filter(stock -> {
                // Price filter
                if (filters.containsKey("minPrice") && stock.getPrice() < (Double) filters.get("minPrice")) {
                    return false;
                }
                if (filters.containsKey("maxPrice") && stock.getPrice() > (Double) filters.get("maxPrice")) {
                    return false;
                }

                // Change percent filter
                if (filters.containsKey("minChangePercent") && stock.getChangePercent() < (Double) filters.get("minChangePercent")) {
                    return false;
                }
                if (filters.containsKey("maxChangePercent") && stock.getChangePercent() > (Double) filters.get("maxChangePercent")) {
                    return false;
                }

                // PE ratio filter
                if (filters.containsKey("maxPeRatio") && stock.getPeRatio() > (Double) filters.get("maxPeRatio")) {
                    return false;
                }

                // ROE filter
                if (filters.containsKey("minRoe") && stock.getRoe() < (Double) filters.get("minRoe")) {
                    return false;
                }

                // Market cap filter
                if (filters.containsKey("minMarketCap") && stock.getMarketCap() < (Double) filters.get("minMarketCap")) {
                    return false;
                }

                // Volume filter
                if (filters.containsKey("minVolume") && stock.getVolume() < (Double) filters.get("minVolume")) {
                    return false;
                }

                return true;
            })
            .collect(Collectors.toList());
    }
} 