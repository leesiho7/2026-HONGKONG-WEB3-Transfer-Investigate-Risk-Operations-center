package com.tem.quant.repository;

import com.tem.quant.entity.WhaleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WhaleTransactionRepository extends JpaRepository<WhaleTransaction, Long> {

    Optional<WhaleTransaction> findByTxHash(String txHash);

    List<WhaleTransaction> findTop20ByOrderByDetectedAtDesc();

    List<WhaleTransaction> findTop10ByChainNameOrderByDetectedAtDesc(String chainName);

    /** 지난 24시간 총 이체량 (ETH) */
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WhaleTransaction w " +
           "WHERE w.assetSymbol = 'ETH' AND w.detectedAt >= CURRENT_TIMESTAMP - 1 DAY")
    Double sumEthLast24h();

    /** 지난 24시간 총 이체량 (BTC) */
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WhaleTransaction w " +
           "WHERE w.assetSymbol = 'BTC' AND w.detectedAt >= CURRENT_TIMESTAMP - 1 DAY")
    Double sumBtcLast24h();

    long countByRiskLevel(String riskLevel);
}
