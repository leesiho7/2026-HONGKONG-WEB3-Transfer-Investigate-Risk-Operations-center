package com.tem.quant.controller;

import com.tem.quant.service.WhaleFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * QuickNode Streams 웹훅 수신 컨트롤러 — Ethereum Mainnet 전용
 *
 * QuickNode Stream 설정:
 *   - Network : Ethereum Mainnet
 *   - Destination: POST https://<ngrok-url>/api/webhook/quicknode/eth
 *   - Filter  : transaction value >= 0xDE0B6B3A7640000000  (1,000 ETH in Wei)
 *
 * 헬스체크: GET https://<ngrok-url>/api/webhook/quicknode/health
 */
@RestController
@RequestMapping("/api/webhook/quicknode")
@RequiredArgsConstructor
@Slf4j
public class QuickNodeWebhookController {

    private final WhaleFilterService whaleFilterService;

    /** ETH 트랜잭션 웹훅 수신 */
    @PostMapping("/eth")
    public ResponseEntity<String> receiveEthWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-qn-signature", required = false) String signature) {

        log.info("📥 QuickNode ETH 웹훅 수신 (tx count={})", estimateSize(payload));
        new Thread(() -> whaleFilterService.processEthWebhook(payload)).start();
        return ResponseEntity.ok("OK");
    }

    /** ngrok + QuickNode 연결 확인용 헬스체크 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status",   "UP",
            "chain",    "Ethereum Mainnet",
            "endpoint", "POST /api/webhook/quicknode/eth",
            "threshold","1,000 ETH"
        ));
    }

    private int estimateSize(Map<String, Object> payload) {
        try {
            Object data = payload.get("data");
            if (data instanceof java.util.List<?> l) return l.size();
        } catch (Exception ignored) {}
        return -1;
    }
}
