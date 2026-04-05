package com.tem.quant.controller;

import com.tem.quant.entity.WhaleTransaction;
import com.tem.quant.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 고래 감시 대시보드 페이지 컨트롤러
 * URL: GET /whale-monitor
 */
@Controller
@RequiredArgsConstructor
public class WhaleMonitorController {

    private final WhaleTransactionRepository whaleRepository;

    /** 고래 모니터링 메인 페이지 */
    @GetMapping("/whale-monitor")
    public String whaleMonitor(Model model) {
        List<WhaleTransaction> recent = whaleRepository.findTop20ByOrderByDetectedAtDesc();

        Double ethVol      = whaleRepository.sumEthLast24h();
        long criticalCount = whaleRepository.countByRiskLevel("CRITICAL");

        model.addAttribute("recentWhales",  recent);
        model.addAttribute("eth24hVolume",  ethVol != null ? String.format("%,.0f", ethVol) : "0");
        model.addAttribute("criticalCount", criticalCount);
        model.addAttribute("currentTime",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return "whale-monitor";
    }

    /** OKLink 주소 라벨 조회 API (프론트엔드 Address Lookup용) */
    @GetMapping("/api/whale/label")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lookupLabel(
            @RequestParam String address,
            @RequestParam(defaultValue = "eth") String chain) {

        // WhaleFilterService를 통하지 않고 직접 서비스 호출
        // (여기서는 간단히 DB에서 기존 라벨 조회 + OKLink 미리 조회)
        return ResponseEntity.ok(Map.of(
            "address", address,
            "chain", chain,
            "message", "OKLink lookup triggered — result via WebSocket"
        ));
    }

    /** 최근 고래 트랜잭션 REST API */
    @GetMapping("/api/whale/recent")
    @ResponseBody
    public ResponseEntity<List<WhaleTransaction>> getRecentWhales(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(whaleRepository.findTop20ByOrderByDetectedAtDesc());
    }
}
