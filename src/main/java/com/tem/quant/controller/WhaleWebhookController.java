package com.tem.quant.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tem.quant.service.WhaleFilterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController // 뷰(HTML)가 아닌 데이터만 주고받으므로 RestController 권장
@RequestMapping("/api/whale")
@RequiredArgsConstructor
@Slf4j
public class WhaleWebhookController {

    private final WhaleFilterService filterService;

    /** QuickNode에서 던져주는 ETH 고래 데이터를 받는 엔드포인트 */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleEthWebhook(@RequestBody Map<String, Object> payload) {
        // 1. (선택사항) 여기서 퀵노드 시크릿 헤더 검증
        
        // 2. 서비스로 데이터 토스
        filterService.processEthWebhook(payload);
        
        // 3. 퀵노드에게 잘 받았다고 응답
        return ResponseEntity.ok("ACK");
    }
}