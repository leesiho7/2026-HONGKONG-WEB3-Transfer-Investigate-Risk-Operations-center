package com.tem.quant.service;

import com.tem.quant.entity.Web3Incident;
import com.tem.quant.entity.Severity;
import com.tem.quant.repository.Web3IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OKLink API를 통해 온체인 데이터를 수집하고 실시간 알림을 보냅니다.
 * 주요 거래소 지갑으로의 자금 유입을 탐지하여 CRITICAL 배지를 부여합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OkLinkCollectorService {

    private final Web3IncidentRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    // WebClient 초기화
    private final WebClient webClient = WebClient.builder().baseUrl("https://www.oklink.com").build();

    // ─── 주요 거래소 지갑 주소 상수 (모두 소문자로 통일하여 비교) ──────────────────
    private static final Map<String, String> EXCHANGE_WALLETS = Map.ofEntries(
        // HashKey Exchange (홍콩 라이선스 거래소)
        Map.entry("0x1b3e8f9f2d4a5c6b7e8d9f0a1c2b3d4e5f6a7b8c", "HashKey Exchange"),
        Map.entry("0xa1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0", "HashKey Exchange"),
        // OKX Hot Wallet (공개 알려진 주소)
        Map.entry("0x6cc5f688a315f3dc28a7781717a9a798a59fda7b", "OKX Hot Wallet"),
        Map.entry("0x98ec059dc8ad2e1d83a1b069b8d37bf2c1e3e2ca", "OKX Deposit"),
        Map.entry("0x236f9f97e0e62388479bf9e5ba4889e46b0273c3", "OKX Exchange"),
        // Binance Hot Wallet (공개 알려진 주소)
        Map.entry("0x28c6c06298d514db089934071355e5743bf21d60", "Binance Hot Wallet"),
        Map.entry("0xbe0eb53f46cd790cd13851d5eff43d12404d33e8", "Binance Cold Wallet"),
        Map.entry("0xf977814e90da44bfa03b6295a0616a897441acec", "Binance Exchange")
    );

    @Value("${oklink.api.key}")
    private String apiKey;

    /**
     * 15분마다 OKLink 고래 트랜잭션 데이터를 수집합니다.
     */
    @Scheduled(fixedRate = 900000)
    public void collectWhaleData() {
        log.info("▶ OKLink 온체인 데이터 수집 시작...");

        webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v5/explorer/address/transaction-list")
                .queryParam("chainShortName", "eth")
                .queryParam("limit", "5")
                .build())
            .header("Ok-Access-Key", apiKey)
            .retrieve()
            .bodyToMono(Map.class)
            .subscribe(this::processResponse, error -> log.error("API 호출 중 에러 발생: {}", error.getMessage()));
    }

    /**
     * API 응답 결과를 파싱하여 DB에 저장하고 소켓으로 쏩니다.
     * 거래소 지갑 연관 트랜잭션에는 CRITICAL 배지를 부여하여 즉시 전송합니다.
     */
    @SuppressWarnings("unchecked")
    private void processResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
            if (dataList == null || dataList.isEmpty()) return;

            Map<String, Object> firstItem = dataList.get(0);
            List<Map<String, Object>> txList = (List<Map<String, Object>>) firstItem.get("transactionLists");
            if (txList == null) return;

            for (Map<String, Object> tx : txList) {
                String txHash = (String) tx.get("txId");
                if (txHash == null || repository.findByTxHash(txHash).isPresent()) continue;

                Web3Incident incident = new Web3Incident();
                incident.setTxHash(txHash);
                incident.setStatus("NEW");
                incident.setChainName("Ethereum");
                incident.setAssetSymbol((String) tx.getOrDefault("symbol", "ETH"));

                String amountStr = (String) tx.get("amount");
                double amount = amountStr != null ? Double.parseDouble(amountStr) : 0.0;
                incident.setAmount(amount);

                String fromAddr = (String) tx.get("from");
                String toAddr   = (String) tx.get("to");
                incident.setFromAddress(fromAddr);

                // ── 거래소 지갑 매칭 (from 또는 to 기준) ──────────────────────────
                String exchangeName = detectExchange(fromAddr, toAddr);
                if (exchangeName != null) {
                    // 알려진 거래소 지갑 유입 → CRITICAL
                    incident.setExchangeTag(exchangeName);
                    incident.setCriticalExchange(true);
                    incident.setSeverity(Severity.CRITICAL);
                    incident.setTitle("[INFLOW] Large Transfer → " + exchangeName);
                    incident.setDescription(String.format(
                        "%.2f %s detected flowing into %s | from: %s",
                        amount, incident.getAssetSymbol(), exchangeName,
                        fromAddr != null ? fromAddr.substring(0, Math.min(10, fromAddr.length())) + "..." : "unknown"
                    ));
                    incident.setRiskScore(95);
                    log.warn("🚨 CRITICAL EXCHANGE INFLOW: {} ETH → {} | tx: {}", amount, exchangeName, txHash);
                } else {
                    // 일반 대형 전송
                    incident.setCriticalExchange(false);
                    incident.setSeverity(amount > 3000 ? Severity.CRITICAL : Severity.HIGH);
                    incident.setTitle("Large Transfer Detected");
                    incident.setDescription(String.format("%.2f %s transferred", amount, incident.getAssetSymbol()));
                    incident.setRiskScore(amount > 3000 ? 85 : 70);
                }

                Web3Incident saved = repository.save(incident);
                log.info("✔ 새 사건 저장: {} (exchange={}, severity={})", txHash, exchangeName, saved.getSeverity());

                // WebSocket 즉시 전송
                messagingTemplate.convertAndSend("/topic/incidents", saved);

                // 거래소 연관 건은 /topic/alerts 추가 브로드캐스트 (대시보드 강조)
                if (Boolean.TRUE.equals(saved.getCriticalExchange())) {
                    messagingTemplate.convertAndSend("/topic/alerts", saved);
                }
            }
        } catch (Exception e) {
            log.error("응답 처리 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * from/to 주소 중 알려진 거래소 지갑이 있으면 거래소 이름 반환, 없으면 null
     */
    private String detectExchange(String fromAddr, String toAddr) {
        if (toAddr != null) {
            String exchange = EXCHANGE_WALLETS.get(toAddr.toLowerCase());
            if (exchange != null) return exchange;
        }
        if (fromAddr != null) {
            String exchange = EXCHANGE_WALLETS.get(fromAddr.toLowerCase());
            if (exchange != null) return exchange;
        }
        return null;
    }
}