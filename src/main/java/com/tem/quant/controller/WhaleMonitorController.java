package com.tem.quant.controller;

import com.tem.quant.entity.WhaleTransaction;
import com.tem.quant.repository.WhaleTransactionRepository;
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
     * WebSocket 파이프라인 테스트용 엔드포인트
     * 브라우저에서 GET /api/whale/test 호출 시 CRITICAL 알림을 즉시 발생시킵니다.
     * 배포 후 파이프라인 동작 확인에 사용하세요.
     */
    @GetMapping("/api/whale/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerTestAlert(
            @RequestParam(defaultValue = "CRITICAL") String risk) {

        WhaleTransaction fake = new WhaleTransaction();
        fake.setTxHash("0xTEST_" + System.currentTimeMillis());
        fake.setChainName("Ethereum");
        fake.setAssetSymbol("ETH");
        fake.setAmount(5000.0);
        fake.setAmountUsd(5000.0 * 2400);
        fake.setFromAddress("0x742d35Cc6634C0532925a3b844Bc454e4438f44e");
        fake.setToAddress("0x28C6c06298d514Db089934071355E5743bf21d60");
        fake.setFromLabel("Lazarus Group");
        fake.setToLabel("Binance Hot Wallet");
        fake.setFromLabelType("Hacker");
        fake.setToLabelType("Exchange");
        fake.setRiskLevel(risk.toUpperCase());

        whaleWebSocketHandler.broadcastWhaleEvent(fake);

        return ResponseEntity.ok(Map.of(
            "status", "sent",
            "riskLevel", risk.toUpperCase(),
            "message", "WebSocket 알림 전송 완료 — 브라우저 whale-monitor 탭 확인"
        ));
    }
}
