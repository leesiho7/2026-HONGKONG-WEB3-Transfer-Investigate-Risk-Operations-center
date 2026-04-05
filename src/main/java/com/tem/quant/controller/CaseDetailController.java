package com.tem.quant.controller;

import com.tem.quant.entity.Web3Incident;
import com.tem.quant.service.IncidentService;
import com.tem.quant.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/case")
@RequiredArgsConstructor
public class CaseDetailController {

    private final IncidentService incidentService;
    private final TimelineService timelineService;

    /** 1. 상세 데이터 조회 */
    @GetMapping("/{caseId}")
    public ResponseEntity<Web3Incident> getCaseDetail(@PathVariable String caseId) {
        return incidentService.getIncidentByCaseId(caseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 2. 담당자 할당 — Assign to Me */
    @PostMapping("/{caseId}/assign")
    public ResponseEntity<Map<String, Object>> assignCase(
            @PathVariable String caseId, @RequestBody Map<String, String> payload) {

        String operator = payload.getOrDefault("operator", "Operator-7");
        Web3Incident incident = incidentService.getIncidentByCaseId(caseId)
                .orElseThrow(() -> new RuntimeException("사건을 찾을 수 없습니다."));

        incident.setAssignee(operator);
        incident.setStatus("INVESTIGATING");
        incidentService.save(incident);

        timelineService.logAction(incident, operator, "Case Assigned",
                operator + " assigned the case and started investigation.");

        return ResponseEntity.ok(Map.of(
                "status", "INVESTIGATING",
                "assignee", operator,
                "message", "담당자가 할당되었습니다."
        ));
    }

    /** 3. 조사 노트(코멘트) 추가 — 타임라인에 기록 + 실시간 브로드캐스트 */
    @PostMapping("/{caseId}/comment")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable String caseId, @RequestBody Map<String, String> payload) {

        String content  = payload.getOrDefault("content", "");
        String operator = payload.getOrDefault("operator", "Operator-7");
        if (content.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Empty comment"));

        Web3Incident incident = incidentService.getIncidentByCaseId(caseId)
                .orElseThrow(() -> new RuntimeException("사건을 찾을 수 없습니다."));

        timelineService.logAction(incident, operator, "Investigation Note", content);

        return ResponseEntity.ok(Map.of(
                "operator", operator,
                "comment", content,
                "message", "코멘트가 저장되었습니다."
        ));
    }

    /** 4. 케이스 에스컬레이션 */
    @PostMapping("/{caseId}/escalate")
    public ResponseEntity<Map<String, Object>> escalateCase(
            @PathVariable String caseId, @RequestBody Map<String, String> payload) {

        String operator = payload.getOrDefault("operator", "Operator-7");
        Web3Incident incident = incidentService.getIncidentByCaseId(caseId)
                .orElseThrow(() -> new RuntimeException("사건을 찾을 수 없습니다."));

        incident.setStatus("ESCALATED");
        incidentService.save(incident);

        timelineService.logAction(incident, operator, "Case Escalated",
                "Escalated to senior analyst by " + operator + ".");

        return ResponseEntity.ok(Map.of("status", "ESCALATED", "message", "에스컬레이션 완료."));
    }

    /** 5. 케이스 해결(Resolve) */
    @PostMapping("/{caseId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveCase(
            @PathVariable String caseId, @RequestBody Map<String, String> payload) {

        String operator = payload.getOrDefault("operator", "Operator-7");
        Web3Incident incident = incidentService.getIncidentByCaseId(caseId)
                .orElseThrow(() -> new RuntimeException("사건을 찾을 수 없습니다."));

        incident.setStatus("RESOLVED");
        incidentService.save(incident);

        timelineService.logAction(incident, operator, "Case Resolved",
                "Marked as resolved by " + operator + ". Investigation complete.");

        return ResponseEntity.ok(Map.of("status", "RESOLVED", "message", "케이스가 종결되었습니다."));
    }
}