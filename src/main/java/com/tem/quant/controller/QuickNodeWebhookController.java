package com.tem.quant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tem.quant.service.WebhookQueueConsumer;
import com.tem.quant.service.WhaleFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * QuickNode Streams 웹훅 수신 컨트롤러 — Ethereum Mainnet 전용
 *
 * 처리 흐름:
 *   POST /eth → Redis LPUSH (whale:webhook:eth) → 즉시 200 OK 반환
 *   (Redis 장애 시 webhookTaskExecutor 직접 처리로 폴백, 데이터 유실 없음)
 *
 * QuickNode Stream 설정:
 *   - Network : Ethereum Mainnet
 *   - Destination: POST https://calm-elegance-production.up.railway.app/api/webhook/quicknode/eth
 *   - Filter  : transaction value >= 0x3635C9ADC5DEA00000  (1,000 ETH in Wei)
 */
@RestController
@RequestMapping("/api/webhook/quicknode")
@Slf4j
public class QuickNodeWebhookController {

    private final WhaleFilterService whaleFilterService;
    private final StringRedisTemplate redisTemplate;
    private final Executor webhookTaskExecutor;
    private final ObjectMapper objectMapper;

    public QuickNodeWebhookController(WhaleFilterService whaleFilterService,
                                       StringRedisTemplate redisTemplate,
                                       @Qualifier("webhookTaskExecutor") Executor webhookTaskExecutor,
                                       ObjectMapper objectMapper) {
        this.whaleFilterService  = whaleFilterService;
        this.redisTemplate       = redisTemplate;
        this.webhookTaskExecutor = webhookTaskExecutor;
        this.objectMapper        = objectMapper;
    }

    /** ETH 트랜잭션 웹훅 수신 */
    @PostMapping("/eth")
    public ResponseEntity<String> receiveEthWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-qn-signature", required = false) String signature) {

        int txCount = estimateSize(payload);
        log.info("📥 QuickNode ETH 웹훅 수신 (tx count={})", txCount);

        try {
            // payload → JSON 직렬화 → Redis List에 적재 (즉시 반환)
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForList().leftPush(WebhookQueueConsumer.QUEUE_KEY, json);
        } catch (Exception e) {
            // Redis 장애 시: bounded executor로 직접 처리 (데이터 유실 방지)
            log.warn("⚠️  Redis 큐잉 실패 → executor 직접 처리 폴백: {}", e.getMessage());
            webhookTaskExecutor.execute(() -> whaleFilterService.processEthWebhook(payload));
        }

        return ResponseEntity.ok("OK");
    }

    /** Railway + QuickNode 연결 확인용 헬스체크 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status",    "UP",
            "chain",     "Ethereum Mainnet",
            "endpoint",  "POST /api/webhook/quicknode/eth",
            "threshold", "1,000 ETH",
            "queueSize", getQueueSize()
        ));
    }

    private int estimateSize(Map<String, Object> payload) {
        try {
            Object data = payload.get("data");
            if (data instanceof java.util.List<?> l) return l.size();
        } catch (Exception ignored) {}
        return -1;
    }

    private long getQueueSize() {
        try {
            Long size = redisTemplate.opsForList().size(WebhookQueueConsumer.QUEUE_KEY);
            return size != null ? size : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }
}
