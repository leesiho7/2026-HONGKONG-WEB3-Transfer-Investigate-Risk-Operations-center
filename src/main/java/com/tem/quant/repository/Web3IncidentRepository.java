package com.tem.quant.repository;

import com.tem.quant.entity.Web3Incident;
import com.tem.quant.entity.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface Web3IncidentRepository extends JpaRepository<Web3Incident, Long> {

    // 1. 사이드바용: 최신순으로 5개 가져오기
    List<Web3Incident> findTop5ByOrderByCreatedAtDesc();

    // 2. 리스크 점수 계산용: 특정 심각도(Severity)의 개수 세기 (빨간 줄 해결 포인트!)
    long countBySeverity(Severity severity);

    // 3. 데이터 수집기(Collector)용: 트랜잭션 해시로 중복 여부 확인
    Optional<Web3Incident> findByTxHash(String txHash);
    
 
    
    // 4. 상세 페이지 조회를 위한 Case ID 검색 (추가됨!)
    Optional<Web3Incident> findByCaseId(String caseId);
}