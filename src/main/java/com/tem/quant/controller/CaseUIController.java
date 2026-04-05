package com.tem.quant.controller;

import com.tem.quant.entity.Web3Incident;
import com.tem.quant.entity.InvestigationTimeline;
import com.tem.quant.service.IncidentService;
import com.tem.quant.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class CaseUIController {

    private final IncidentService incidentService;
    private final TimelineRepository timelineRepository;

    @GetMapping("/case/{caseId}")
    public String getCaseDetail(@PathVariable String caseId, Model model) {
        Web3Incident incident = incidentService.getIncidentByCaseId(caseId)
                .orElseThrow(() -> new RuntimeException("사건을 찾을 수 없습니다."));

        // 타임라인 조회
        List<InvestigationTimeline> timelines = timelineRepository.findByIncidentOrderByTimestampDesc(incident);

        // 템플릿 요구사항에 맞게 데이터 가공 (case 객체 생성)
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("id", incident.getId());
        caseData.put("caseId", incident.getCaseId());
        caseData.put("status", incident.getStatus());
        caseData.put("priority", "P1");
        caseData.put("title", incident.getTitle());
        caseData.put("description", incident.getDescription() != null ? incident.getDescription() : "No description");
        caseData.put("createdAgo", incident.getTimeAgo());
        caseData.put("assignee", incident.getAssignee() != null ? incident.getAssignee() : "Unassigned");
        caseData.put("chain", incident.getChainName());
        caseData.put("riskScore", incident.getRiskScore() != null ? incident.getRiskScore() : 92);
        
        caseData.put("timeline", timelines.stream().map(t -> {
            Map<String, Object> event = new HashMap<>();
            event.put("operator", t.getOperatorName());
            event.put("timeAgo", "Just now");
            event.put("description", t.getAction() + ": " + t.getComment());
            return event;
        }).collect(Collectors.toList()));
        
        caseData.put("comments", List.of());
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("transferAmount", incident.getAmount() + " " + incident.getAssetSymbol());
        metrics.put("usdValue", "$" + String.format("%.2f", incident.getAmount() * 2000));
        metrics.put("walletBalance", "892,300 ETH");
        metrics.put("previousTransfers", "14 transfers > $50M");
        metrics.put("riskIndicators", List.of("Large transfer", "Whale wallet", "High volatility"));
        caseData.put("metrics", metrics);
        
        caseData.put("relatedWallet", incident.getFromAddress());

        model.addAttribute("case", caseData);
        model.addAttribute("incidents", incidentService.getRecentIncidents());
        model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")));

        return "dashboard-detail";
    }
}
