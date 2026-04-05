package com.tem.quant.websoketconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

/**
 * Web3j Bean 설정
 * QuickNode WebSocket 엔드포인트로 연결합니다.
 *
 * application.properties:
 *   quicknode.ethereum.endpoint=https://...quiknode.pro/...
 *   → wss:// 로 자동 변환하여 WebSocket 구독에 사용
 */
@Configuration
@Slf4j
public class Web3jConfig {

    @Value("${quicknode.ethereum.endpoint:}")
    private String httpEndpoint;

    @Bean
    public Web3j web3j() {
        // HTTP → WSS 자동 변환
        String wsEndpoint = httpEndpoint
            .replace("https://", "wss://")
            .replace("http://",  "ws://");

        if (wsEndpoint.isBlank()) {
            log.warn("⚠️  quicknode.ethereum.endpoint 미설정 — Web3j HTTP fallback 사용");
            return Web3j.build(new org.web3j.protocol.http.HttpService(
                "https://cloudflare-eth.com"   // 공개 fallback RPC
            ));
        }

        try {
            WebSocketService wsService = new WebSocketService(wsEndpoint, false);
            wsService.connect();
            log.info("✅ Web3j WebSocket 연결 성공: {}", wsEndpoint.substring(0, Math.min(40, wsEndpoint.length())) + "...");
            return Web3j.build(wsService);
        } catch (Exception e) {
            log.warn("⚠️  WebSocket 연결 실패 ({}), HTTP fallback 사용: {}", wsEndpoint, e.getMessage());
            return Web3j.build(new org.web3j.protocol.http.HttpService(httpEndpoint));
        }
    }
}
