package com.tem.quant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Redis 웹훅 큐 컨슈머
 *
 * 구조:
 *   QuickNodeWebhookController → LPUSH whale:webhook:eth
 *   [consumer-0, consumer-1]   → BRPOP → webhookTaskExecutor → processEthWebhook()
 *
 * - 컨슈머 스레드는 2개로 고정 (Redis I/O 전담, 경량)
 * - 실제 처리는 webhookTaskExecutor (ThreadPoolTaskExecutor) 에 위임
 * - CallerRunsPolicy 로 풀 포화 시 컨슈머 스레드가 직접 처리 → 자동 백프레셔
 */
@Component
@Slf4j
public class WebhookQueueConsumer implements DisposableBean {

    /** Redis List 키 (controller와 공유) */
    public static final String QUEUE_KEY = "whale:webhook:eth";

    private static final int CONSUMER_COUNT = 2;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final WhaleFilterService whaleFilterService;
    private final Executor webhookTaskExecutor;
    private final ObjectMapper objectMapper;

    private volatile boolean running = false;

    public WebhookQueueConsumer(StringRedisTemplate redisTemplate,
                                 WhaleFilterService whaleFilterService,
                                 @Qualifier("webhookTaskExecutor") Executor webhookTaskExecutor,
                                 ObjectMapper objectMapper) {
        this.redisTemplate       = redisTemplate;
        this.whaleFilterService  = whaleFilterService;
        this.webhookTaskExecutor = webhookTaskExecutor;
        this.objectMapper        = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsumers() {
        running = true;
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            Thread t = new Thread(this::consumeLoop, "redis-consumer-" + i);
            t.setDaemon(true);
            t.start();
        }
        log.info("✅ Redis webhook consumer 시작 (count={})", CONSUMER_COUNT);
    }

    private void consumeLoop() {
        while (running) {
            try {
                // BRPOP: 최대 2초 대기, 큐 비어있으면 null 반환
                String json = redisTemplate.opsForList()
                        .rightPop(QUEUE_KEY, Duration.ofSeconds(2));

                if (json == null) continue;

                Map<String, Object> payload = objectMapper.readValue(json, MAP_TYPE);

                // webhookTaskExecutor 에 처리 위임 (CallerRunsPolicy: 포화 시 본 스레드가 직접 처리)
                webhookTaskExecutor.execute(() -> whaleFilterService.processEthWebhook(payload));

            } catch (Exception e) {
                if (running) {
                    log.error("Redis consumer 오류 (재시도 대기 1s): {}", e.getMessage());
                    try { Thread.sleep(1_000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("Redis consumer 종료: {}", Thread.currentThread().getName());
    }

    /** 현재 큐 적체 건수 반환 (모니터링용) */
    public long queueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0L;
    }

    @Override
    public void destroy() {
        running = false;
    }
}
