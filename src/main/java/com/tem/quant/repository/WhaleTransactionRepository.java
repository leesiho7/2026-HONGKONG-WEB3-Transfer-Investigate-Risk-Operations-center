package com.tem.quant.repository;

import com.tem.quant.entity.WhaleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WhaleTransactionRepository extends JpaRepository<WhaleTransaction, Long> {

    Optional<WhaleTransaction> findByTxHash(String txHash);

    List<WhaleTransaction> findTop20ByOrderByDetectedAtDesc();

    List<WhaleTransaction> findTop10ByChainNameOrderByDetectedAtDesc(String chainName);

    /** 지난 24시간 총 이체량 (ETH) — JPQL + 파라미터로 네이밍 전략 무관하게 동작 */
    @Query("SELECT COALESCE(SUM(w.amount), 0.0) FROM WhaleTransaction w " +
           "WHERE w.assetSymbol = 'ETH' AND w.detectedAt >= :since")
    Double sumEthLast24h(@Param("since") LocalDateTime since);

    /** 지난 24시간 총 이체량 (BTC) */
    @Query("SELECT COALESCE(SUM(w.amount), 0.0) FROM WhaleTransaction w " +
           "WHERE w.assetSymbol = 'BTC' AND w.detectedAt >= :since")
    Double sumBtcLast24h(@Param("since") LocalDateTime since);

    long countByRiskLevel(String riskLevel);
}
