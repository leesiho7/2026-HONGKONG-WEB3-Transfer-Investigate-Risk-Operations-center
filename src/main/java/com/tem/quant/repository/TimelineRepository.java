package com.tem.quant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tem.quant.entity.InvestigationTimeline;
import com.tem.quant.entity.Web3Incident;

@Repository

public interface TimelineRepository extends JpaRepository<InvestigationTimeline, Long> {
    // 특정 사건의 타임라인만 최신순으로 가져오기
    List<InvestigationTimeline> findByIncidentOrderByTimestampDesc(Web3Incident incident);
}