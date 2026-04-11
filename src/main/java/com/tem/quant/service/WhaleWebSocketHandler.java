package com.tem.quant.service;

import com.tem.quant.entity.WhaleTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhaleWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final GeographicMapper geoMapper;

    /**
     * 가공된 고래 트랜잭션 데이터를 프론트엔드 지도 시각화 형식에 맞춰 브로드캐스트합니다.
     */
    public void broadcastWhaleEvent(WhaleTransaction tx) {
        try {
            // 1. null 방어 로직 (Map.of 대신 HashMap을 사용하여 NPE 방지)
            Map<String, Object> payload = new HashMap<>();
            
            String fromLabel = (tx.getFromLabel() != null) ? tx.getFromLabel() : "Unknown";
            String toLabel = (tx.getToLabel() != null) ? tx.getToLabel() : "Unknown";

            payload.put("txHash", (tx.getTxHash() != null) ? tx.getTxHash() : "0x000...");
            payload.put("amount", tx.getAmount());
            payload.put("assetSymbol", (tx.getAssetSymbol() != null) ? tx.getAssetSymbol() : "ETH");
            payload.put("riskLevel", (tx.getRiskLevel() != null) ? tx.getRiskLevel() : "INFO");
            payload.put("fromLabel", fromLabel);
            payload.put("toLabel", toLabel);
            
            // 2. GeographicMapper를 통한 허브 좌표 매핑
            payload.put("fromHub", geoMapper.mapToHub(fromLabel));
            payload.put("toHub", geoMapper.mapToHub(toLabel));
            
            payload.put("amountUsd", (tx.getAmountUsd() != null) ? tx.getAmountUsd() : 0.0);

            // 3. WebSocket 전송 (/topic/whale-alerts 채널)
            messagingTemplate.convertAndSend("/topic/whale-alerts", payload);
            
            log.info("🚀 [WebSocket] 지도 데이터 전송 완료: {} -> {} ({} ETH)", 
                payload.get("fromHub"), payload.get("toHub"), tx.getAmount());

        } catch (Exception e) {
            log.error("❌ [WebSocket] 전송 중 오류 발생: {}", e.getMessage());
        }
    }
}