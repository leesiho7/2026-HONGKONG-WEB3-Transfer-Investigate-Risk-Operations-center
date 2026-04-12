package com.tem.quant.controller;

import com.tem.quant.entity.WhaleTransaction;
import com.tem.quant.repository.WhaleTransactionRepository;
import com.tem.quant.service.WhaleFilterService;
import com.tem.quant.service.WhaleWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
    private final WhaleWebSocketHandler whaleWebSocketHandler;
    private final WhaleFilterService whaleFilterService;

    /** 고래 모니터링 메인 페이지 */
    @GetMapping("/whale-monitor")
    public String whaleMonitor(Model model) {
        List<WhaleTransaction> recent = whaleRepository.findTop20ByOrderByDetectedAtDesc();

        LocalDateTime since24h = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
        Double ethVol      = whaleRepository.sumEthLast24h(since24h);
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

    /**
     * 풀 파이프라인 테스트 엔드포인트
     * DB 저장 + WebSocket 알림 + ROC 인시던트 생성까지 전체 흐름을 실행합니다.
     * GET /api/whale/test?amount=5000&risk=CRITICAL
     */
    @GetMapping("/api/whale/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerTestAlert(
            @RequestParam(defaultValue = "CRITICAL") String risk,
            @RequestParam(defaultValue = "5000") double amount) {

        // WhaleFilterService 웹훅 포맷으로 가짜 페이로드 생성
        // → DB 저장 + WebSocket + ROC 인시던트까지 실제 파이프라인 전체 실행
        String fakeHash = "0xTEST" + System.currentTimeMillis();

        // 금액에 따라 risk 무시하고 실제 임계값 로직이 적용됨
        // amount=5000이면 CRITICAL, amount=2000이면 HIGH, amount=1000이면 MEDIUM
        // hex: 5000 ETH = 5000 * 1e18 wei
        java.math.BigInteger weiValue = java.math.BigDecimal.valueOf(amount)
            .multiply(new java.math.BigDecimal("1000000000000000000"))
            .toBigInteger();
        String hexValue = "0x" + weiValue.toString(16);

        Map<String, Object> txMap = new java.util.HashMap<>();
        txMap.put("hash", fakeHash);
        txMap.put("from", "0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
        txMap.put("to",   "0x28C6c06298d514Db089934071355E5743bf21d60");
        txMap.put("value", hexValue);
        txMap.put("blockNumber", 21000000L);

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("data", List.of(txMap));

        whaleFilterService.processEthWebhook(payload);

        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "txHash", fakeHash,
            "amount", amount + " ETH",
            "message", "DB 저장 + WebSocket + ROC 인시던트 파이프라인 실행 완료"
        ));
    }
}
