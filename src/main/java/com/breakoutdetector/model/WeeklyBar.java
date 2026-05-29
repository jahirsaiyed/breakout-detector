package com.breakoutdetector.model;

import java.time.LocalDate;

/** Immutable split/dividend-adjusted weekly OHLCV bar. */
public record WeeklyBar(LocalDate date, double open, double high, double low, double close, long volume) {}
