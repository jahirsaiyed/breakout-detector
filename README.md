# Breakout Detector

A comprehensive Spring Boot application for detecting stock market breakouts and identifying potential multibagger stocks. The application uses advanced technical analysis algorithms to identify breakout patterns similar to the SHILCTEC chart you shared.

## Features

### 🔍 **Enhanced Breakout Detection Algorithms**

#### **Volume-Based Strategies:**
- **Volume Breakouts**: Detects unusual volume spikes (>2x average)
- **Bullish Volume Breakouts**: Volume spike with price increase (>2%)
- **Bearish Volume Breakouts**: Volume spike with price decrease (>2%)
- **Volume Climax**: Extreme volume spikes (>3x average)

#### **Price-Based Strategies:**
- **Price Breakouts**: Identifies price breaks above resistance levels
- **Price Range Breakouts**: Breaks above 20-day price range high
- **Price Range Breakdowns**: Breaks below 20-day price range low

#### **Trend Line Strategies:**
- **Resistance Trend Line Breakouts**: Breaks above calculated resistance trend line
- **Support Trend Line Breakdowns**: Breaks below calculated support trend line

#### **Technical Indicator Strategies:**
- **RSI Divergence**: Finds bullish divergence patterns
- **Bollinger Band Breakouts**: Detects breaks above upper bands
- **MACD Crossovers**: Identifies bullish MACD crossovers

### 📊 **Data Sources**
- **Yahoo Finance API**: Free, reliable data
- **Alpha Vantage API**: Professional-grade data (requires API key)
- **NSE India API**: Indian stock market data
- **Screener.in API**: Indian stock screening
- **StockAnalysis.com API**: Comprehensive US stock data

### 🧪 **Backtesting Engine**
- **Strategy Validation**: Test breakout strategies on historical data
- **Performance Metrics**: Sharpe ratio, win rate, profit factor
- **Risk Management**: Stop-loss and take-profit automation
- **Portfolio Simulation**: Realistic trading simulation

### 🎯 **Screening Queries**

#### **StockAnalysis.com Queries:**
```
- Price > 50 (avoid penny stocks)
- Volume > 100K (adequate liquidity)
- Price Change 1D > 5% (momentum)
- Price Change 1M > 20% (trend strength)
- RSI(14) > 70 (momentum confirmation)
- Volume > Average Volume 20D (volume confirmation)
```

#### **Screener.in Queries:**
```
- Market Cap > 500 Cr
- Current Price > 50
- 1 Month Return > 20%
- 3 Month Return > 50%
- Volume > 20 Day Average Volume
- Price > 20 Day Moving Average
- Price > 50 Day Moving Average
```

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Internet connection for data fetching

### Installation

1. **Clone the repository:**
```bash
git clone <repository-url>
cd BreakoutDetector
```

2. **Build the application:**
```bash
mvn clean install
```

3. **Run the application:**
```bash
mvn spring-boot:run
```

4. **Access the application:**
- API Base URL: `http://localhost:8080/breakout-detector/api`
- H2 Console: `http://localhost:8080/breakout-detector/h2-console`

## API Usage

### 1. Detect Breakouts
```bash
# Detect breakouts for a stock
curl "http://localhost:8080/breakout-detector/api/breakout/detect/AAPL?source=yahoo"
```

### 2. Filter Signals
```bash
# Filter signals with criteria
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/filter" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "source": "yahoo",
    "filters": {
      "minVolumeRatio": 2.0,
      "minRSI": 50,
      "signalType": "VOLUME_BREAKOUT"
    }
  }'
```

### 3. Backtest Strategy
```bash
# Backtest breakout strategy
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/backtest" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "AAPL",
    "source": "yahoo",
    "filters": {
      "minVolumeRatio": 1.5,
      "signalType": "PRICE_BREAKOUT"
    }
  }'
```

### 4. Get Stock Data
```bash
# Fetch stock data
curl "http://localhost:8080/breakout-detector/api/breakout/data/AAPL?source=yahoo"
```

### 5. Compare Data Sources
```bash
# Compare multiple data sources
curl "http://localhost:8080/breakout-detector/api/breakout/compare/AAPL"
```

### 6. Detect Staircase Pattern
```bash
# Detect staircase pattern for multibagger identification
curl "http://localhost:8080/breakout-detector/api/breakout/staircase/SHILCTEC?source=yahoo"
```

### 7. Get Multibagger Score
```bash
# Calculate multibagger score for a stock
curl "http://localhost:8080/breakout-detector/api/breakout/multibagger-score/SHILCTEC?source=yahoo"
```

### 8. Find Staircase Patterns (Screening)
```bash
# Screen multiple stocks for staircase patterns
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/find-staircase-patterns" \
  -H "Content-Type: application/json" \
  -d '{
    "symbols": ["SHILCTEC", "RELIANCE", "TCS"],
    "source": "yahoo"
  }'
```

### 9. Get Screener Dashboard
```bash
# Get comprehensive screener dashboard with all predefined queries
curl "http://localhost:8080/breakout-detector/api/breakout/screener/dashboard"
```

### 10. Execute Screener Query
```bash
# Execute a specific screener query
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/screener/execute" \
  -H "Content-Type: application/json" \
  -d '{
    "queryName": "Multibagger Momentum",
    "source": "stockanalysis"
  }'
```

### 11. Execute Multiple Screener Queries
```bash
# Execute multiple screener queries at once
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/screener/execute-multiple" \
  -H "Content-Type: application/json" \
  -d '{
    "queryNames": ["Multibagger Momentum", "Breakout Candidates", "Volume Spikes"]
  }'
```

### 12. Get Predefined Screener Queries
```bash
# Get all available screener queries
curl "http://localhost:8080/breakout-detector/api/breakout/screener/queries"
```

### 13. Filter Screener Results
```bash
# Filter screener results based on criteria
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/screener/filter" \
  -H "Content-Type: application/json" \
  -d '{
    "stocks": [...],
    "filters": {
      "minPrice": 50,
      "maxPrice": 200,
      "minChangePercent": 2,
      "maxPeRatio": 25,
      "minRoe": 15
    }
  }'
```

## Breakout Detection Methods

### **Volume-Based Breakout Detection**

#### **1. Volume Breakout Detection**
- **Criteria**: Volume > 2x 20-day average volume
- **Signal**: `VOLUME_BREAKOUT`
- **Confirmation**: Multiple volume averages and price action

#### **2. Bullish Volume Breakout**
- **Criteria**: Volume spike with >2% price increase
- **Signal**: `VOLUME_BREAKOUT_BULLISH`
- **Confirmation**: Volume > 2x average with rising price

#### **3. Bearish Volume Breakout**
- **Criteria**: Volume spike with >2% price decrease
- **Signal**: `VOLUME_BREAKOUT_BEARISH`
- **Confirmation**: Volume > 2x average with falling price

#### **4. Volume Climax**
- **Criteria**: Extreme volume > 3x average
- **Signal**: `VOLUME_CLIMAX`
- **Confirmation**: Unusual volume activity

### **Price-Based Breakout Detection**

#### **5. Price Breakout Detection**
- **Criteria**: Price breaks above resistance level by 2%
- **Signal**: `PRICE_BREAKOUT`
- **Confirmation**: Volume > 1.5x previous day volume

#### **6. Price Range Breakout**
- **Criteria**: Price breaks above 20-day range high by 2%
- **Signal**: `PRICE_RANGE_BREAKOUT`
- **Confirmation**: Volume > 1.5x average volume

#### **7. Price Range Breakdown**
- **Criteria**: Price breaks below 20-day range low by 2%
- **Signal**: `PRICE_RANGE_BREAKDOWN`
- **Confirmation**: Volume > 1.5x average volume

### **Trend Line Breakout Detection**

#### **8. Resistance Trend Line Breakout**
- **Criteria**: Price breaks above calculated resistance trend line by 1%
- **Signal**: `TRENDLINE_RESISTANCE_BREAKOUT`
- **Confirmation**: Volume > 1.3x average volume

#### **9. Support Trend Line Breakdown**
- **Criteria**: Price breaks below calculated support trend line by 1%
- **Signal**: `TRENDLINE_SUPPORT_BREAKDOWN`
- **Confirmation**: Volume > 1.3x average volume

### **Technical Indicator Breakout Detection**

#### **10. RSI Divergence Detection**
- **Criteria**: RSI making higher lows while price makes lower lows
- **Signal**: `RSI_DIVERGENCE`
- **Confirmation**: RSI > 50 and price starting to move up

#### **11. Bollinger Band Breakout**
- **Criteria**: Price breaks above upper Bollinger Band
- **Signal**: `BOLLINGER_BREAKOUT`
- **Confirmation**: Volume > 1.5x average volume

#### **12. MACD Crossover**
- **Criteria**: MACD line crosses above signal line
- **Signal**: `MACD_CROSSOVER`
- **Confirmation**: Volume > 1.2x average volume

## 🏗️ **Staircase Pattern Detection**

### **Early-Stage Multibagger Identification**
The system now includes specialized detection for **staircase patterns** - a key characteristic of early-stage multibagger stocks like SHILCTEC.

#### **Staircase Pattern Criteria:**
- **Minimum 3 Breakouts**: Progressive price breakouts over time
- **Progressive Breakouts**: Each breakout higher than the previous
- **Reasonable Pullbacks**: Maximum 15% pullback between breakouts
- **Volume Confirmation**: Volume spikes with each breakout
- **Pattern Strength**: STRONG, MODERATE, or WEAK classification

#### **Multibagger Score Calculation:**
- **Breakout Count**: 10 points per breakout
- **Price Increase**: Points based on total price increase
- **Pattern Strength**: 30 points for STRONG, 15 for MODERATE
- **Volume Breakouts**: 5 points per volume breakout
- **Penalty**: -5 points per breakdown

#### **Score Levels:**
- **VERY_HIGH (80+)**: Excellent multibagger potential
- **HIGH (60-79)**: Good multibagger potential
- **MEDIUM (40-59)**: Moderate potential
- **LOW (20-39)**: Limited potential
- **VERY_LOW (<20)**: Low potential

## 🔍 **Stock Screener Integration**

### **Automated Screening for Multibagger Stocks**
The system integrates with **StockAnalysis.com** and **Screener.in** to automatically identify potential multibagger stocks using predefined queries.

#### **Predefined Screener Queries:**

##### **StockAnalysis.com Queries:**
- **Multibagger Momentum**: Stocks with strong momentum and volume breakouts
- **Breakout Candidates**: Stocks breaking out of resistance with high volume
- **Volume Spikes**: Stocks with unusual volume activity
- **Strong Fundamentals**: Stocks with strong fundamentals and momentum

##### **Screener.in Queries:**
- **NSE Multibagger**: NSE stocks with multibagger potential
- **NSE Breakout**: NSE stocks showing breakout patterns
- **NSE Volume Leaders**: NSE stocks with highest volume activity
- **NSE Strong Fundamentals**: NSE stocks with strong fundamentals

#### **Screener Features:**
- **Real-time Data**: Live stock data from multiple sources
- **Advanced Filtering**: Filter by price, PE ratio, ROE, volume, etc.
- **Comprehensive Dashboard**: Overview of all screener results
- **Batch Processing**: Execute multiple queries simultaneously
- **Custom Filters**: Apply additional criteria to screener results

## Backtesting Results

The backtesting engine provides comprehensive metrics:

- **Total Return**: Overall profit/loss
- **Win Rate**: Percentage of profitable trades
- **Sharpe Ratio**: Risk-adjusted returns
- **Maximum Drawdown**: Largest peak-to-trough decline
- **Profit Factor**: Gross profit / Gross loss
- **Average Trade Return**: Mean return per trade

## Configuration

### Data Source Configuration
```properties
# Default data source
app.data.default-source=yahoo

# Available sources
app.data.sources=yahoo,alphavantage,nse,screener,stockanalysis
```

### Breakout Detection Parameters
```properties
# RSI period
app.breakout.rsi-period=14

# MACD parameters
app.breakout.macd-fast=12
app.breakout.macd-slow=26
app.breakout.macd-signal=9

# Moving average periods
app.breakout.volume-ma-period=20
app.breakout.price-ma-period=20
app.breakout.resistance-lookback=50
```

### Backtesting Parameters
```properties
# Initial capital
app.backtest.initial-capital=100000

# Position sizing
app.backtest.position-size=0.1

# Risk management
app.backtest.stop-loss-percent=0.05
app.backtest.take-profit-percent=0.20
app.backtest.max-hold-days=30
```

## Finding Multibagger Stocks

### **Step 1: Initial Screening**
Use the screener queries provided above to identify stocks with:
- Strong momentum (price and volume)
- Above key moving averages
- High relative strength

### **Step 2: Breakout Analysis**
Run the breakout detection on promising stocks:
```bash
curl "http://localhost:8080/breakout-detector/api/breakout/detect/SHILCTEC?source=yahoo"
```

### **Step 3: Backtesting Validation**
Validate the strategy on historical data:
```bash
curl -X POST "http://localhost:8080/breakout-detector/api/breakout/backtest" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "SHILCTEC",
    "source": "yahoo"
  }'
```

### **Step 4: Portfolio Monitoring**
- Monitor detected breakouts daily
- Track performance metrics
- Adjust parameters based on results

## Comparison of Methods

### **Screener-Based Approach**
- ✅ **Pros**: Quick initial filtering, user-friendly
- ❌ **Cons**: Limited customization, basic analysis

### **TradingView Approach**
- ✅ **Pros**: Advanced charting, real-time alerts
- ❌ **Cons**: Manual analysis, limited automation

### **Spring Boot Application (Recommended)**
- ✅ **Pros**: 
  - Full automation and customization
  - Multiple data sources
  - Comprehensive backtesting
  - Scalable architecture
  - Real-time monitoring
- ❌ **Cons**: Requires technical setup

## Advanced Usage

### **Custom Breakout Strategies**
You can extend the `BreakoutDetectionService` to add custom strategies:

```java
private List<BreakoutSignal> detectCustomBreakout(List<StockData> stockDataList, TimeSeries series) {
    // Implement your custom breakout logic
    // Add to the main detectBreakouts method
}
```

### **Real-time Monitoring**
Set up scheduled tasks to monitor stocks:

```java
@Scheduled(fixedRate = 300000) // Every 5 minutes
public void monitorBreakouts() {
    // Fetch latest data and detect breakouts
    // Send alerts for new signals
}
```

### **Portfolio Management**
Extend the backtesting service to manage multiple positions:

```java
public class PortfolioManager {
    // Track multiple positions
    // Implement position sizing
    // Risk management across portfolio
}
```

## Troubleshooting

### **Common Issues**

1. **Data Fetching Errors**
   - Check internet connection
   - Verify API keys (if using paid services)
   - Try different data sources

2. **No Breakout Signals**
   - Adjust detection parameters
   - Check if stock has sufficient data
   - Verify stock symbol format

3. **Backtesting Errors**
   - Ensure sufficient historical data
   - Check signal date alignment
   - Verify data quality

### **Performance Optimization**
- Use caching for frequently accessed data
- Implement batch processing for multiple stocks
- Optimize database queries

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add your improvements
4. Submit a pull request

## License

This project is licensed under the MIT License.

## Disclaimer

This application is for educational and research purposes only. Stock market investments carry risk, and past performance does not guarantee future results. Always conduct thorough research and consider consulting with financial advisors before making investment decisions. 


🎯 Key Features:
1. Advanced Breakout Detection Algorithms
Volume Breakouts: Detects unusual volume spikes (>2x average)
Price Breakouts: Identifies breaks above resistance levels
RSI Divergence: Finds bullish divergence patterns
Bollinger Band Breakouts: Detects momentum continuation
MACD Crossovers: Identifies trend confirmations
2. Multiple Data Sources
Yahoo Finance (free, reliable)
Alpha Vantage (professional)
NSE India (Indian stocks)
Screener.in (Indian screening)
StockAnalysis.com (US stocks)
3. Comprehensive Backtesting Engine
Historical strategy validation
Risk-adjusted performance metrics
Portfolio simulation
Stop-loss and take-profit automation
4. Web Interface
User-friendly dashboard
Real-time analysis
Interactive charts
API endpoints
📊 Comparison of Methods:
Method	Reliability	Customization	Automation
Screener Queries	⭐⭐	⭐	⭐
TradingView	⭐⭐⭐	⭐⭐	⭐⭐
Spring Boot App	⭐⭐⭐⭐⭐	⭐⭐⭐⭐⭐	⭐⭐⭐⭐⭐
�� Quick Start:
Build and run:
Apply to index.html
Run
Access the application:
Web Interface: http://localhost:8080/breakout-detector/
API: http://localhost:8080/breakout-detector/api/breakout
Test with SHILCTEC:
Apply to index.html
Run
🎯 Why This is the Best Solution:
🔬 Advanced Analysis: Multiple technical indicators and algorithms
📊 Multiple Data Sources: Comprehensive market coverage
🧪 Backtesting: Validate strategies on historical data
⚡ Real-time Monitoring: Automated signal detection
🎛️ Customizable: Extensible for custom strategies
📈 Scalable: Can handle multiple stocks and markets
📋 Screening Queries (Quick Start):
StockAnalysis.com:
Apply to index.html
Screener.in:
Apply to index.html
The Spring Boot application provides the most comprehensive and reliable approach to finding breakout stocks like SHILCTEC. It combines advanced technical analysis, multiple data sources, comprehensive backtesting, and real-time monitoring capabilities.
Next Steps:
Deploy the application
Test with historical data
Start screening for new opportunities
Monitor and refine your approach
This solution will help you identify multibagger stocks by detecting early breakout patterns with high accuracy and providing the tools to validate and optimize your strategies.