package com.tem.quant.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Convert;

import com.tem.quant.repository.AddressLabelRepository;
import com.tem.quant.repository.WhaleLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionMonitorService {

    private final AddressLabelRepository addressLabelRepository;
    private final WhaleLogRepository whaleLogRepository;
    private final Web3j web3j;
    private final WhaleFilterService whaleFilterService;

    // WhaleFilterService와 동일한 임계값 사용 (application.properties: whale.eth.threshold)
    @Value("${whale.eth.threshold:1000}")
    private double whaleThresholdEth;

    @EventListener(ApplicationReadyEvent.class)
    public void startMonitoring() {
        log.info("🚀 실시간 고래 탐지 및 라벨링 모니터링 시스템 가동...");
        try {
            // HTTP 프로바이더(Cloudflare fallback)는 transactionFlowable() 미지원 → 서버 크래시 방지
            web3j.transactionFlowable().subscribe(tx -> {
                if (tx.getValue() != null) {
                    BigDecimal amount = Convert.fromWei(tx.getValue().toString(), Convert.Unit.ETHER);
                    if (amount.compareTo(BigDecimal.valueOf(whaleThresholdEth)) >= 0) {
                        processWhaleTransaction(tx, amount);
                    }
                }
            }, error -> log.error("❌ 모니터링 중 에러 발생: ", error));
        } catch (Exception e) {
            log.warn("⚠️ Web3j 스트리밍 구독 불가 (HTTP 프로바이더는 미지원). " +
                     "QuickNode WebSocket 엔드포인트를 설정하면 실시간 모니터링이 활성화됩니다. 원인: {}", e.getMessage());
        }
    }

    private void processWhaleTransaction(Transaction tx, BigDecimal amount) {
        String from = tx.getFrom() != null ? tx.getFrom().toLowerCase() : "unknown";
        String to   = tx.getTo()   != null ? tx.getTo().toLowerCase()   : "contract_creation";

        log.info("🐋 [Whale Alert] {} ETH 이동 감지! {} → {}", amount, getSafeAddress(from), getSafeAddress(to));

        // WhaleFilterService 포맷으로 변환하여 처리
        // → DB 저장 + WebSocket 알림 + ROC 인시던트 생성까지 일괄 처리됨
        try {
            Map<String, Object> txMap = new HashMap<>();
            txMap.put("hash", tx.getHash());
            txMap.put("from", from);
            txMap.put("to", to);
            txMap.put("value", tx.getValue() != null ? "0x" + tx.getValue().toString(16) : "0x0");
            if (tx.getBlockNumber() != null) {
                txMap.put("blockNumber", tx.getBlockNumber().longValue());
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("data", List.of(txMap));

            whaleFilterService.processEthWebhook(payload);
        } catch (Exception e) {
            log.error("❌ WhaleFilterService 처리 실패: {}", e.getMessage());
        }
    }

    /**
     * 주소 문자열을 안전하게 자르는 헬퍼 메서드
     * 에러 로그에 나왔던 StringIndexOutOfBoundsException을 원천 차단합니다.
     */
    private String getSafeAddress(String address) {
        if (address == null) return "Unknown";
        // 주소가 10자보다 짧으면 그대로 반환, 길면 앞 10자만 표시 (로그 가독성용)
        return address.length() > 10 ? address.substring(0, 10) + "..." : address;
    }
}