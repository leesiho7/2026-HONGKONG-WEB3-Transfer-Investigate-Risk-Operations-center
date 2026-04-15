package com.tem.quant.service;

import com.tem.quant.entity.Web3Incident;
import com.tem.quant.entity.Severity;
import com.tem.quant.repository.Web3IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Web3 관제 및 조사 시스템의 핵심 비즈니스 로직
 * 대시보드 리스트 조회 및 상세 페이지(Case Detail) 데이터 처리를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

    private final Web3IncidentRepository repository;

    /**
     * 1. 메인 리스트용: 최신 100건만 조회 (Critical Alerts 영역)
     *    findAll() 전체 로딩 → Pageable(100건) 으로 변경, 메모리 사용량 대폭 절감
     */
    @Transactional(readOnly = true)
    public List<Web3Incident> getAllIncidents() {
        try {
            Pageable pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
            return repository.findAll(pageable).getContent();
        } catch (Exception e) {
            log.error("전체 사건 조회 중 오류 발생: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 2. 사이드바용: 최신 5건만 조회 (Active Incidents 영역)
     */
    @Transactional(readOnly = true)
    public List<Web3Incident> getRecentIncidents() {
        return repository.findTop5ByOrderByCreatedAtDesc();
    }

    /**
     * 3. 상세 페이지용: Case ID로 특정 사건 조회
     * 빌더 포인트: 상세 페이지(image_ac464a.png) 렌더링 시 사용됩니다.
     */
    @Transactional(readOnly = true)
    public Optional<Web3Incident> getIncidentByCaseId(String caseId) {
        return repository.findByCaseId(caseId);
    }

    /**
     * 4. 리스크 점수 계산: CRITICAL 사건 개수에 따라 동적 가중치 부여
     */
    @Transactional(readOnly = true)
    public int calculateOverallRiskScore() {
        try {
            long criticalCount = repository.countBySeverity(Severity.CRITICAL);
            // 기본 70점에서 시작하여 사건당 5점 추가 (최대 99점)
            int score = (int) Math.min(70 + (criticalCount * 5), 99);
            return score;
        } catch (Exception e) {
            log.error("리스크 점수 계산 중 오류 발생: {}", e.getMessage());
            return 70;
        }
    }

    /**
     * 5. 사건 상태 업데이트 및 저장 (Assign to Me, Resolve 등 액션 대응)
     */
    @Transactional
    public Web3Incident save(Web3Incident incident) {
        log.info("사건 데이터 업데이트 실행: {}", incident.getCaseId());
        return repository.save(incident);
    }
}