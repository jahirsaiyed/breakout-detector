package com.breakoutdetector.repository;

import com.breakoutdetector.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByScanDateOrderByRankScoreDesc(LocalDate scanDate);

    List<Candidate> findBySymbolOrderByScanDateDesc(String symbol);

    @org.springframework.data.jpa.repository.Query("select max(c.scanDate) from Candidate c")
    Optional<LocalDate> findLatestScanDate();
}
