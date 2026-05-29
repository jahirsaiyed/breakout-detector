package com.breakoutdetector.service;

import com.breakoutdetector.model.StockData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DataFetchingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DataFetchingService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Fetch stock data from Alpha Vantage API
     */
    public List<StockData> fetchFromAlphaVantage(String symbol, String apiKey) {
        try {
            String url = String.format(
                "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                symbol, apiKey
            );

            String response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode timeSeries = rootNode.get("Time Series (Daily)");

            if (timeSeries == null) {
                return new ArrayList<>();
            }

            List<StockData> stockDataList = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            timeSeries.fields().forEachRemaining(entry -> {
                String date = entry.getKey();
                JsonNode data = entry.getValue();

                try {
                    StockData stockData = new StockData(
                        symbol,
                        LocalDateTime.parse(date + "T00:00:00"),
                        new BigDecimal(data.get("1. open").asText()),
                        new BigDecimal(data.get("2. high").asText()),
                        new BigDecimal(data.get("3. low").asText()),
                        new BigDecimal(data.get("4. close").asText()),
                        Long.parseLong(data.get("5. volume").asText())
                    );

                    stockDataList.add(stockData);
                } catch (Exception e) {
                    // Skip invalid data
                }
            });

            return stockDataList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from Alpha Vantage for " + symbol, e);
        }
    }

    /**
     * Fetch stock data from Yahoo Finance API
     */
    public List<StockData> fetchFromYahooFinance(String symbol) {
        try {
            // Yahoo Finance API endpoint
            String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1y",
                symbol
            );

            String response = webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode chart = rootNode.get("chart");
            JsonNode result = chart.get("result").get(0);
            JsonNode timestamp = result.get("timestamp");
            JsonNode indicators = result.get("indicators");
            JsonNode quote = indicators.get("quote").get(0);

            JsonNode open = quote.get("open");
            JsonNode high = quote.get("high");
            JsonNode low = quote.get("low");
            JsonNode close = quote.get("close");
            JsonNode volume = quote.get("volume");

            List<StockData> stockDataList = new ArrayList<>();

            for (int i = 0; i < timestamp.size(); i++) {
                try {
                    long timestampValue = timestamp.get(i).asLong();
                    LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestampValue, 0, java.time.ZoneOffset.UTC);

                    StockData stockData = new StockData(
                        symbol,
                        dateTime,
                        new BigDecimal(open.get(i).asText()),
                        new BigDecimal(high.get(i).asText()),
                        new BigDecimal(low.get(i).asText()),
                        new BigDecimal(close.get(i).asText()),
                        Long.parseLong(volume.get(i).asText())
                    );

                    stockDataList.add(stockData);
                } catch (Exception e) {
                    // Skip invalid data
                }
            }

            return stockDataList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from Yahoo Finance for " + symbol, e);
        }
    }

    /**
     * Fetch Indian stock data from NSE/BSE
     */
    public List<StockData> fetchIndianStockData(String symbol) {
        try {
            // Using a proxy service or direct NSE API
            String url = String.format(
                "https://www.nseindia.com/api/historical/cm/equity?symbol=%s",
                symbol
            );

            String response = webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode data = rootNode.get("data");

            List<StockData> stockDataList = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

            for (JsonNode item : data) {
                try {
                    String dateStr = item.get("date").asText();
                    LocalDateTime dateTime = LocalDateTime.parse(dateStr + "T00:00:00", 
                        DateTimeFormatter.ofPattern("dd-MMM-yyyy'T'HH:mm:ss"));

                    StockData stockData = new StockData(
                        symbol,
                        dateTime,
                        new BigDecimal(item.get("open").asText()),
                        new BigDecimal(item.get("high").asText()),
                        new BigDecimal(item.get("low").asText()),
                        new BigDecimal(item.get("close").asText()),
                        Long.parseLong(item.get("volume").asText())
                    );

                    stockDataList.add(stockData);
                } catch (Exception e) {
                    // Skip invalid data
                }
            }

            return stockDataList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Indian stock data for " + symbol, e);
        }
    }

    /**
     * Fetch data from Screener.in
     */
    public List<StockData> fetchFromScreenerIn(String symbol) {
        try {
            // Screener.in API endpoint
            String url = String.format(
                "https://www.screener.in/api/company/%s/",
                symbol
            );

            String response = webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode quarterlyResults = rootNode.get("quarterly_results");

            List<StockData> stockDataList = new ArrayList<>();

            // Process quarterly results to get price data
            for (JsonNode quarter : quarterlyResults) {
                try {
                    String period = quarter.get("period").asText();
                    LocalDateTime dateTime = parseQuarterPeriod(period);

                    StockData stockData = new StockData(
                        symbol,
                        dateTime,
                        new BigDecimal(quarter.get("open").asText()),
                        new BigDecimal(quarter.get("high").asText()),
                        new BigDecimal(quarter.get("low").asText()),
                        new BigDecimal(quarter.get("close").asText()),
                        Long.parseLong(quarter.get("volume").asText())
                    );

                    stockDataList.add(stockData);
                } catch (Exception e) {
                    // Skip invalid data
                }
            }

            return stockDataList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from Screener.in for " + symbol, e);
        }
    }

    /**
     * Fetch data from StockAnalysis.com
     */
    public List<StockData> fetchFromStockAnalysis(String symbol) {
        try {
            // StockAnalysis.com API endpoint
            String url = String.format(
                "https://stockanalysis.com/api/v1/stocks/%s/history",
                symbol
            );

            String response = webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode data = rootNode.get("data");

            List<StockData> stockDataList = new ArrayList<>();

            for (JsonNode item : data) {
                try {
                    String dateStr = item.get("date").asText();
                    LocalDateTime dateTime = LocalDateTime.parse(dateStr + "T00:00:00");

                    StockData stockData = new StockData(
                        symbol,
                        dateTime,
                        new BigDecimal(item.get("open").asText()),
                        new BigDecimal(item.get("high").asText()),
                        new BigDecimal(item.get("low").asText()),
                        new BigDecimal(item.get("close").asText()),
                        Long.parseLong(item.get("volume").asText())
                    );

                    stockDataList.add(stockData);
                } catch (Exception e) {
                    // Skip invalid data
                }
            }

            return stockDataList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from StockAnalysis.com for " + symbol, e);
        }
    }

    /**
     * Get stock data from multiple sources and merge
     */
    public List<StockData> fetchStockData(String symbol, String source) {
        switch (source.toLowerCase()) {
            case "alphavantage":
                return fetchFromAlphaVantage(symbol, "YOUR_API_KEY"); // Replace with actual API key
            case "yahoo":
                return fetchFromYahooFinance(symbol);
            case "nse":
                return fetchIndianStockData(symbol);
            case "screener":
                return fetchFromScreenerIn(symbol);
            case "stockanalysis":
                return fetchFromStockAnalysis(symbol);
            default:
                throw new IllegalArgumentException("Unsupported data source: " + source);
        }
    }

    /**
     * Parse quarter period string to LocalDateTime
     */
    private LocalDateTime parseQuarterPeriod(String period) {
        // Handle different quarter period formats
        // Example: "Q1 2023", "Mar 2023", etc.
        try {
            if (period.contains("Q")) {
                // Handle quarterly format
                String[] parts = period.split(" ");
                int quarter = Integer.parseInt(parts[0].substring(1));
                int year = Integer.parseInt(parts[1]);
                int month = (quarter - 1) * 3 + 1;
                return LocalDateTime.of(year, month, 1, 0, 0);
            } else {
                // Handle month format
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
                return LocalDateTime.parse(period, formatter);
            }
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * Validate stock data
     */
    public boolean validateStockData(List<StockData> stockDataList) {
        if (stockDataList == null || stockDataList.isEmpty()) {
            return false;
        }

        for (StockData data : stockDataList) {
            if (data.getClose().compareTo(BigDecimal.ZERO) <= 0 ||
                data.getVolume().compareTo(0L) <= 0) {
                return false;
            }
        }

        return true;
    }
} 