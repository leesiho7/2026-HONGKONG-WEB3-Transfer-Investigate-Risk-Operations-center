package com.tem.quant.controller;

import com.tem.quant.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Web3 Risk Operations Center (ROC) 메인 컨트롤러
 * 빌더 포인트: 실시간 관제 센터의 느낌을 주기 위해 현재 시간과 리스크 점수를 동적으로 전달
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final IncidentService incidentService;

    @GetMapping({"/", "/dashboard"})
    public String index(Model model) {
        // 1. 메인 리스트 데이터 (전체 사건 목록)
        // HTML의 th:each="alert : ${alerts}" 부분과 매칭됩니다.
        model.addAttribute("alerts", incidentService.getAllIncidents());
        
        // 2. 사이드바 전용 (최근 사건 5건)
        // HTML의 th:each="incident : ${incidents}" 부분과 매칭됩니다.
        model.addAttribute("incidents", incidentService.getRecentIncidents());
        
        // 3. 리스크 분석 점수
        // HTML의 th:text="${riskScore}" 부분과 매칭됩니다.
        model.addAttribute("riskScore", incidentService.calculateOverallRiskScore());
        
        // 4. 현재 시간 (관제 센터 실시간 느낌 연출)
        model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")));
        
        // 5. 리스크 요인 (임시 데이터)
        model.addAttribute("riskFactors", List.of(
            Map.of("name", "Whale Activity", "description", "Large wallet movements", "score", 85),
            Map.of("name", "New Smart Contracts", "description", "Unverified contract deploy", "score", 45),
            Map.of("name", "Social Sentiment", "description", "Negative trend detected", "score", 60)
        ));

        return "dashboard"; 
    }
}