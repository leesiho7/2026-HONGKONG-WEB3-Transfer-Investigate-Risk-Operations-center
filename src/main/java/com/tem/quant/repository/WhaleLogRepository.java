package com.tem.quant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tem.quant.entity.WhaleLog;


@Repository
public interface WhaleLogRepository extends JpaRepository<WhaleLog, Long> {
    
    // 최근 발생한 고래 거래 순으로 가져오기 (대시보드용)
    List<WhaleLog> findTop10ByOrderByBlockTimeDesc();

    // 특정 금액 이상인 거래만 조회
    List<WhaleLog> findByAmountGreaterThanEqual(java.math.BigDecimal amount);
}