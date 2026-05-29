package com.breakoutdetector.controller;

import com.breakoutdetector.model.BreakoutSignal;
import com.breakoutdetector.model.StockData;
import com.breakoutdetector.service.BacktestingService;
import com.breakoutdetector.service.BreakoutDetectionService;
import com.breakoutdetector.service.DataFetchingService;
import com.breakoutdetector.service.StaircasePatternService;
import com.breakoutdetector.service.ScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/breakout")
@CrossOrigin(origins = "*")
@Tag(name = "Breakout Detector", description = "Stock breakout detection, staircase pattern analysis, backtesting, and screening")
public class BreakoutController {

    @Autowired
    private BreakoutDetectionService breakoutDetectionService;

    @Autowired
    private DataFetchingService dataFetchingService;

    @Autowired
    private BacktestingService backtestingService;

    @Autowired
    private StaircasePatternService staircasePatternService;

    @Autowired
    private ScreenerService screenerService;

    @Operation(summary = "Detect breakouts", description = "Detect breakout signals for a specific stock symbol")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Breakout signals returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or insufficient stock data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/detect/{symbol}")
    public ResponseEntity<Map<String, Object>> detectBreakouts(
            @Parameter(description = "Stock ticker symbol (e.g. AAPL, NVDA)", required = true) @PathVariable String symbol,
            @Parameter(description = "Data source: yahoo, alphavantage, nse, screener, stockanalysis") @RequestParam(defaultValue = "yahoo") String source) {
        
        try {
            // Fetch stock data
            List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
            
            if (!dataFetchingService.validateStockData(stockDataList)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or insufficient stock data"));
            }

            // Detect breakouts
            List<BreakoutSignal> signals = breakoutDetectionService.detectBreakouts(stockDataList);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("totalSignals", signals.size());
            response.put("signals", signals);
            response.put("dataPoints", stockDataList.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to detect breakouts: " + e.getMessage()));
        }
    }

    @Operation(summary = "Filter breakout signals", description = "Fetch breakouts for a symbol and apply filter criteria")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Filtered signals returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or insufficient stock data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/filter")
    public ResponseEntity<Map<String, Object>> filterSignals(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "symbol, source (optional), filters (optional map)")
            @RequestBody Map<String, Object> request) {
        
        try {
            String symbol = (String) request.get("symbol");
            String source = (String) request.getOrDefault("source", "yahoo");
            Map<String, Object> filters = (Map<String, Object>) request.get("filters");

            // Fetch stock data
            List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
            
            if (!dataFetchingService.validateStockData(stockDataList)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or insufficient stock data"));
            }

            // Detect all breakouts
            List<BreakoutSignal> allSignals = breakoutDetectionService.detectBreakouts(stockDataList);

            // Apply filters
            List<BreakoutSignal> filteredSignals = breakoutDetectionService.filterSignals(allSignals, filters);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("totalSignals", allSignals.size());
            response.put("filteredSignals", filteredSignals.size());
            response.put("signals", filteredSignals);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to filter signals: " + e.getMessage()));
        }
    }

    @Operation(summary = "Backtest strategy", description = "Run a backtest on detected breakout signals for a symbol")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Backtest result returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or insufficient stock data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/backtest")
    public ResponseEntity<Map<String, Object>> backtestStrategy(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "symbol, source (optional), filters (optional map)")
            @RequestBody Map<String, Object> request) {
        
        try {
            String symbol = (String) request.get("symbol");
            String source = (String) request.getOrDefault("source", "yahoo");
            Map<String, Object> filters = (Map<String, Object>) request.get("filters");

            // Fetch stock data
            List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
            
            if (!dataFetchingService.validateStockData(stockDataList)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or insufficient stock data"));
            }

            // Detect breakouts
            List<BreakoutSignal> allSignals = breakoutDetectionService.detectBreakouts(stockDataList);

            // Apply filters if provided
            List<BreakoutSignal> signals = allSignals;
            if (filters != null && !filters.isEmpty()) {
                signals = breakoutDetectionService.filterSignals(allSignals, filters);
            }

            // Run backtest
            BacktestingService.BacktestResult result = backtestingService.backtestStrategy(stockDataList, signals);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("backtestResult", result);
            response.put("totalSignals", signals.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to backtest strategy: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get stock data", description = "Fetch raw OHLCV data for a symbol from the specified source")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock data returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or insufficient stock data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/data/{symbol}")
    public ResponseEntity<Map<String, Object>> getStockData(
            @Parameter(description = "Stock ticker symbol", required = true) @PathVariable String symbol,
            @Parameter(description = "Data source") @RequestParam(defaultValue = "yahoo") String source) {
        
        try {
            List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
            
            if (!dataFetchingService.validateStockData(stockDataList)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or insufficient stock data"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("dataPoints", stockDataList.size());
            response.put("data", stockDataList);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to fetch stock data: " + e.getMessage()));
        }
    }

    @Operation(summary = "Compare data sources", description = "Compare stock data availability and latest price across all supported data sources")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comparison result returned"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/compare/{symbol}")
    public ResponseEntity<Map<String, Object>> compareDataSources(
            @Parameter(description = "Stock ticker symbol", required = true) @PathVariable String symbol) {
        
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);

            // Try different data sources
            String[] sources = {"yahoo", "alphavantage", "nse", "screener", "stockanalysis"};
            
            for (String source : sources) {
                try {
                    List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
                    if (dataFetchingService.validateStockData(stockDataList)) {
                        response.put(source, Map.of(
                            "dataPoints", stockDataList.size(),
                            "firstDate", stockDataList.get(0).getTimestamp(),
                            "lastDate", stockDataList.get(stockDataList.size() - 1).getTimestamp(),
                            "latestPrice", stockDataList.get(stockDataList.size() - 1).getClose()
                        ));
                    }
                } catch (Exception e) {
                    response.put(source, Map.of("error", e.getMessage()));
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to compare data sources: " + e.getMessage()));
        }
    }

    @Operation(summary = "List data sources", description = "Returns all supported data sources and their descriptions")
    @ApiResponse(responseCode = "200", description = "Data sources returned")
    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> getDataSources() {
        Map<String, Object> sources = Map.of(
            "yahoo", "Yahoo Finance API",
            "alphavantage", "Alpha Vantage API (requires API key)",
            "nse", "NSE India API",
            "screener", "Screener.in API",
            "stockanalysis", "StockAnalysis.com API"
        );

        return ResponseEntity.ok(Map.of("sources", sources));
    }

    @Operation(summary = "Detect staircase pattern", description = "Detect staircase breakout patterns for multibagger identification")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Staircase dashboard returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or insufficient stock data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/staircase/{symbol}")
    public ResponseEntity<Map<String, Object>> detectStaircasePattern(
            @Parameter(description = "Stock ticker symbol", required = true) @PathVariable String symbol,
            @Parameter(description = "Data source") @RequestParam(defaultValue = "yahoo") String source) {
        
        try {
            // Fetch stock data
            List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
            
            if (!dataFetchingService.validateStockData(stockDataList)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or insufficient stock data"));
            }

            // Detect breakouts
            List<BreakoutSignal> signals = breakoutDetectionService.detectBreakouts(stockDataList);

            // Get staircase dashboard
            Map<String, Object> dashboard = staircasePatternService.getStaircaseDashboard(stockDataList, signals);

            Map<String, Object> response = new HashMap<>();
            response.put("symbol", symbol);
            response.put("dashboard", dashboard);
            response.put("totalSignals", signals.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to detect staircase pattern: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get multibagger score", description = "Calculate the multibagger probability score for a stock")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Multibagger score returned"),
        @ApiResponse(responseCode = "400", description = "Invalid or insufficient stock data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/multibagger-score/{symbol}")
    public ResponseEntity<Map<String, Object>> getMultibaggerScore(
            @Parameter(description = "Stock ticker symbol", required = true) @PathVariable String symbol,
            @Parameter(description = "Data source") @RequestParam(defaultValue = "yahoo") String source) {
        
        try {
            // Fetch stock data
            List<StockData> stockDataList = dataFetchingService.fetchStockData(symbol, source);
            
            if (!dataFetchingService.validateStockData(stockDataList)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or insufficient stock data"));
            }

            // Detect breakouts
            List<BreakoutSignal> signals = breakoutDetectionService.detectBreakouts(stockDataList);

            // Get multibagger score
            Map<String, Object> score = staircasePatternService.getMultibaggerScore(symbol, stockDataList, signals);

            return ResponseEntity.ok(score);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to calculate multibagger score: " + e.getMessage()));
        }
    }

    @Operation(summary = "Find staircase patterns", description = "Scan a list of symbols and return those exhibiting staircase patterns")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Patterns returned"),
        @ApiResponse(responseCode = "400", description = "No symbols provided"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/find-staircase-patterns")
    public ResponseEntity<Map<String, Object>> findStaircasePatterns(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "symbols (list of tickers), source (optional)")
            @RequestBody Map<String, Object> request) {
        
        try {
            @SuppressWarnings("unchecked")
            List<String> symbols = (List<String>) request.get("symbols");
            String source = (String) request.getOrDefault("source", "yahoo");

            if (symbols == null || symbols.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No symbols provided"));
            }

            Map<String, List<StockData>> stockDataMap = new HashMap<>();
            Map<String, List<BreakoutSignal>> signalsMap = new HashMap<>();

            // Fetch data for all symbols
            for (String symbol : symbols) {
                try {
                    List<StockData> stockData = dataFetchingService.fetchStockData(symbol, source);
                    if (dataFetchingService.validateStockData(stockData)) {
                        stockDataMap.put(symbol, stockData);
                        List<BreakoutSignal> signals = breakoutDetectionService.detectBreakouts(stockData);
                        signalsMap.put(symbol, signals);
                    }
                } catch (Exception e) {
                    // Skip failed symbols
                    continue;
                }
            }

            // Find staircase patterns
            List<StaircasePatternService.StaircasePattern> patterns = 
                staircasePatternService.findStaircasePatterns(symbols, stockDataMap, signalsMap);

            Map<String, Object> response = new HashMap<>();
            response.put("patterns", patterns);
            response.put("totalPatterns", patterns.size());
            response.put("analyzedSymbols", stockDataMap.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to find staircase patterns: " + e.getMessage()));
        }
    }

    @Operation(summary = "Screener dashboard", description = "Run all predefined screener queries and return a combined dashboard")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard returned"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/screener/dashboard")
    public ResponseEntity<Map<String, Object>> getScreenerDashboard() {
        try {
            Map<String, Object> dashboard = screenerService.getScreenerDashboard();
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get screener dashboard: " + e.getMessage()));
        }
    }

    @Operation(summary = "Execute screener query", description = "Run a single named predefined screener query")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Query result returned"),
        @ApiResponse(responseCode = "400", description = "Query not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/screener/execute")
    public ResponseEntity<Map<String, Object>> executeScreenerQuery(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "queryName (string), source (optional)")
            @RequestBody Map<String, Object> request) {
        
        try {
            String queryName = (String) request.get("queryName");
            String source = (String) request.getOrDefault("source", "stockanalysis");
            
            List<ScreenerService.ScreenerQuery> queries = screenerService.getPredefinedQueries();
            ScreenerService.ScreenerQuery selectedQuery = queries.stream()
                .filter(q -> q.getName().equals(queryName))
                .findFirst()
                .orElse(null);

            if (selectedQuery == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Query not found: " + queryName));
            }

            Map<String, Object> result = screenerService.executeScreenerQuery(selectedQuery);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to execute screener query: " + e.getMessage()));
        }
    }

    @Operation(summary = "Execute multiple screener queries", description = "Run multiple named predefined screener queries in one request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Combined results returned"),
        @ApiResponse(responseCode = "400", description = "No valid queries found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/screener/execute-multiple")
    public ResponseEntity<Map<String, Object>> executeMultipleQueries(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "queryNames (list of strings)")
            @RequestBody Map<String, Object> request) {
        
        try {
            @SuppressWarnings("unchecked")
            List<String> queryNames = (List<String>) request.get("queryNames");
            
            if (queryNames == null || queryNames.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No query names provided"));
            }

            List<ScreenerService.ScreenerQuery> allQueries = screenerService.getPredefinedQueries();
            List<ScreenerService.ScreenerQuery> selectedQueries = allQueries.stream()
                .filter(q -> queryNames.contains(q.getName()))
                .collect(Collectors.toList());

            if (selectedQueries.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No valid queries found"));
            }

            Map<String, Object> result = screenerService.executeMultipleQueries(selectedQueries);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to execute multiple queries: " + e.getMessage()));
        }
    }

    @Operation(summary = "List predefined screener queries", description = "Returns all available predefined screener query names and metadata")
    @ApiResponse(responseCode = "200", description = "Queries returned")
    @GetMapping("/screener/queries")
    public ResponseEntity<Map<String, Object>> getPredefinedQueries() {
        try {
            List<ScreenerService.ScreenerQuery> queries = screenerService.getPredefinedQueries();
            Map<String, Object> response = new HashMap<>();
            response.put("queries", queries);
            response.put("totalQueries", queries.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get predefined queries: " + e.getMessage()));
        }
    }

    @Operation(summary = "Filter screener results", description = "Apply filter criteria to a provided list of stocks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Filtered stocks returned"),
        @ApiResponse(responseCode = "400", description = "No stocks data provided"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/screener/filter")
    public ResponseEntity<Map<String, Object>> filterScreenerResults(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "stocks (list of stock objects), filters (map of field → value)")
            @RequestBody Map<String, Object> request) {
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stocksData = (List<Map<String, Object>>) request.get("stocks");
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) request.get("filters");

            if (stocksData == null || stocksData.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No stocks data provided"));
            }

            // Convert to ScreenerResult objects
            List<ScreenerService.ScreenerResult> stocks = new ArrayList<>();
            for (Map<String, Object> stockData : stocksData) {
                ScreenerService.ScreenerResult stock = new ScreenerService.ScreenerResult();
                stock.setSymbol((String) stockData.get("symbol"));
                stock.setName((String) stockData.get("name"));
                stock.setPrice(((Number) stockData.get("price")).doubleValue());
                stock.setChange(((Number) stockData.get("change")).doubleValue());
                stock.setChangePercent(((Number) stockData.get("changePercent")).doubleValue());
                stock.setVolume(((Number) stockData.get("volume")).doubleValue());
                stock.setMarketCap(((Number) stockData.get("marketCap")).doubleValue());
                stock.setPeRatio(((Number) stockData.get("peRatio")).doubleValue());
                stock.setRoe(((Number) stockData.get("roe")).doubleValue());
                stocks.add(stock);
            }

            List<ScreenerService.ScreenerResult> filteredStocks = screenerService.filterStocks(stocks, filters);
            
            Map<String, Object> response = new HashMap<>();
            response.put("filteredStocks", filteredStocks);
            response.put("totalFiltered", filteredStocks.size());
            response.put("originalCount", stocks.size());
            response.put("filters", filters);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to filter screener results: " + e.getMessage()));
        }
    }

    @Operation(summary = "Health check", description = "Returns service health status")
    @ApiResponse(responseCode = "200", description = "Service is up")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Breakout Detector",
            "version", "1.0.0"
        ));
    }
} 