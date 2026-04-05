package com.tem.quant.websoketconfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Web3 Risk Operations Center (ROC) 실시간 통신 설정
 * 빌더 포인트: SockJS를 지원하여 다양한 브라우저 환경에서 안정적인 소켓 연결 보장
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 내보내는 메시지 브로커 설정 (구독 경로: /topic)
        config.enableSimpleBroker("/topic");
        
        // 서버로 보내는 메시지의 접두사 설정 (사용자 -> 서버)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 연결할 엔드포인트 설정: /ws-roc
        // SockJS를 사용하여 소켓을 지원하지 않는 브라우저에서도 통신 가능하게 함
        registry.addEndpoint("/ws-roc")
                .setAllowedOriginPatterns("*") // 모든 도메인 허용 (개발 및 테스트용)
                .withSockJS();
    }
}