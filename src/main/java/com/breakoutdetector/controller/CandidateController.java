package com.breakoutdetector.controller;

import com.breakoutdetector.model.Candidate;
import com.breakoutdetector.repository.CandidateRepository;
import com.breakoutdetector.service.BreakoutScanService;
import com.breakoutdetector.service.BreakoutScanService.ScanSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** API for the long-base-breakout candidate scanner and its track record. */
@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final BreakoutScanService scanService;
    private final CandidateRepository repository;

    public CandidateController(BreakoutScanService scanService, CandidateRepository repository) {
        this.scanService = scanService;
        this.repository = repository;
    }

    /** Trigger a scan now. limit&lt;=0 scans the full universe (slow on a cold cache). */
    @PostMapping("/scan")
    public ResponseEntity<ScanSummary> scan(@RequestParam(defaultValue = "0") int limit) {
        return ResponseEntity.ok(scanService.runScan(limit));
    }

    /** Latest scan's candidates, ranked best-first. recommendedOnly keeps the validated top tercile. */
    @GetMapping("/latest")
    public ResponseEntity<List<Candidate>> latest(
            @RequestParam(defaultValue = "true") boolean recommendedOnly,
            @RequestParam(defaultValue = "0.67") double minPercentile) {
        return repository.findLatestScanDate()
                .map(date -> {
                    List<Candidate> rows = repository.findByScanDateOrderByRankScoreDesc(date);
                    if (recommendedOnly) rows = rows.stream().filter(c -> c.getPercentileRank() >= minPercentile).toList();
                    return ResponseEntity.ok(rows);
                })
                .orElse(ResponseEntity.ok(List.of()));
    }

    /** Candidates for a specific past scan date (track-record browsing). */
    @GetMapping("/by-date/{date}")
    public ResponseEntity<List<Candidate>> byDate(@PathVariable String date) {
        return ResponseEntity.ok(repository.findByScanDateOrderByRankScoreDesc(LocalDate.parse(date)));
    }

    /** Full history for one symbol across scans. */
    @GetMapping("/history/{symbol}")
    public ResponseEntity<List<Candidate>> history(@PathVariable String symbol) {
        return ResponseEntity.ok(repository.findBySymbolOrderByScanDateDesc(symbol));
    }
}
