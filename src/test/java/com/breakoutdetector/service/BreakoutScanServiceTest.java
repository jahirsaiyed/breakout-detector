package com.breakoutdetector.service;

import com.breakoutdetector.model.CandidateExplanation;
import com.breakoutdetector.model.WeeklyBar;
import com.breakoutdetector.repository.CandidateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Explain-path orchestration, with the network (MarketDataService) mocked so CI stays offline. */
class BreakoutScanServiceTest {

    private final CandidateRepository repo = mock(CandidateRepository.class);

    private BreakoutScanService service(MarketDataService md) {
        return new BreakoutScanService(md, new LongBaseBreakoutService(), repo,
                "universe.txt", "SPY", "^NSEI", 8, false);
    }

    private List<WeeklyBar> baseThenBreakout() {
        List<WeeklyBar> bars = new ArrayList<>();
        LocalDate d = LocalDate.of(2015, 1, 5);
        for (int i = 0; i < 250; i++) {
            double c = 100 + Math.sin(i / 6.0) * 4;
            bars.add(new WeeklyBar(d.plusWeeks(i), c, c + 1, c - 1, c, 1_000_000));
        }
        bars.add(new WeeklyBar(d.plusWeeks(250), 106, 116, 105, 115, 3_000_000));
        return bars;
    }

    @Test
    @DisplayName("explain returns a full breakdown for a firing symbol")
    void explainFires() throws Exception {
        MarketDataService md = mock(MarketDataService.class);
        when(md.fetchWeekly(eq("BEN"))).thenReturn(baseThenBreakout());
        when(md.fetchWeekly(eq("SPY"))).thenReturn(List.of());
        when(md.regimeOn(any(), any())).thenReturn(true);

        CandidateExplanation ex = service(md).explain("BEN");

        assertThat(ex.fired()).isTrue();
        assertThat(ex.market()).isEqualTo("US");
        assertThat(ex.factors()).hasSize(6);
        assertThat(ex.reasons()).isNotEmpty();
        assertThat(ex.context()).anyMatch(CandidateExplanation.ContextBar::breakoutWeek);
        assertThat(ex.headline()).contains("BEN");
    }

    @Test
    @DisplayName("explain returns fired=false when there is not enough history")
    void explainNotEnoughData() throws Exception {
        MarketDataService md = mock(MarketDataService.class);
        when(md.fetchWeekly(eq("XYZ"))).thenReturn(baseThenBreakout().subList(0, 30)); // < MIN_BASE_WEEKS

        CandidateExplanation ex = service(md).explain("XYZ");

        assertThat(ex.fired()).isFalse();
        assertThat(ex.factors()).isEmpty();
        assertThat(ex.reasons()).isNotEmpty();
    }

    @Test
    @DisplayName("explain maps a .NS symbol to the India market")
    void explainIndiaMarket() throws Exception {
        MarketDataService md = mock(MarketDataService.class);
        when(md.fetchWeekly(eq("RELIANCE.NS"))).thenReturn(baseThenBreakout());
        when(md.fetchWeekly(eq("^NSEI"))).thenReturn(List.of());
        when(md.regimeOn(any(), any())).thenReturn(true);

        CandidateExplanation ex = service(md).explain("RELIANCE.NS");

        assertThat(ex.market()).isEqualTo("IN");
    }
}
