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
    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM whale_transaction " +
                   "WHERE asset_symbol = 'ETH' AND detected_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)",
           nativeQuery = true)
    Double sumEthLast24h();

    /** 지난 24시간 총 이체량 (BTC) */
    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM whale_transaction " +
                   "WHERE asset_symbol = 'BTC' AND detected_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)",
           nativeQuery = true)
    Double sumBtcLast24h();

    long countByRiskLevel(String riskLevel);
}
