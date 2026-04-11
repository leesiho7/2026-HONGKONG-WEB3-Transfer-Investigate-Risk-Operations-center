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

import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class Web3MonitoringService {

    private final Web3IncidentRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    /** application.properties: whale.demo.enabled=false → 실데이터 모드 */
    @Value("${whale.demo.enabled:true}")
    private boolean demoEnabled;

    /**
     * 데모 모드 전용: 10초마다 시뮬레이션 인시던트 생성
     * whale.demo.enabled=false 이면 실행되지 않습니다.
     */
    @Scheduled(fixedRate = 10000, initialDelay = 2000)
    public void runRealTimeMonitoring() {
        if (!demoEnabled) return;   // 실데이터 모드에서는 비활성화
        log.info("🔍 실시간 온체인 위협 분석 중... (Target: HashKey, OKX, Binance)");
        try {
            // 1. 실제 데이터 분석 결과라고 가정된 리스크 시나리오 생성
            Web3Incident incident = generateAnalysisResult();
            log.debug("▶ generateAnalysisResult 완료: type={}, severity={}", incident.getIncidentType(), incident.getSeverity());

            // 2. DB 저장 (실제 이력 남기기)
            Web3Incident saved = repository.save(incident);
            log.debug("▶ DB 저장 완료: id={}", saved.getId());

            // 3. 웹소켓으로 전송 (채널 다각화)
            messagingTemplate.convertAndSend("/topic/alerts", saved);
            messagingTemplate.convertAndSend("/topic/stats", calculateLiveMetrics(saved));

            log.info("🔥 위협 탐지 완료: {} | type={} | ${}", saved.getTxHash(), saved.getIncidentType(), saved.getUsdValue());
        } catch (Exception e) {
            log.error("❌ [runRealTimeMonitoring] 스케줄 태스크 에러 — {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // 시뮬레이션용 거래소 타겟 (실제 거래소 이름 + 가중치)
    // 시나리오 테이블: {표시명, 인시던트타입, 심각도, 거래소태그}
    private static final String[][] SCENARIOS = {
        {"HashKey Exchange",  "Unusual Exchange Inflow", "CRITICAL", "HashKey Exchange"},
        {"OKX Hot Wallet",    "Unusual Exchange Inflow", "CRITICAL", "OKX Hot Wallet"},
        {"Binance Deposit",   "Unusual Exchange Inflow", "HIGH",     "Binance"},
        {"Unknown Whale",     "Massive Whale Transfer",  "CRITICAL", null},
        {"Unknown Whale",     "Whale Dump Detected",     "HIGH",     null},
        {"Uniswap V3 Pool",   "Liquidity Removal",       "HIGH",     null},
    };

    private Web3Incident generateAnalysisResult() {
        String[] s = SCENARIOS[random.nextInt(SCENARIOS.length)];
        String target       = s[0];
        String incidentType = s[1];
        String severityStr  = s[2];
        String exchangeTag  = s[3];

        double amount    = 500 + (random.nextDouble() * 5000);
        int    riskScore = (int)(70 + (random.nextDouble() * 29));
        boolean isExchange = exchangeTag != null;
        Severity severity  = Severity.valueOf(severityStr);

        Web3Incident incident = new Web3Incident();
        incident.setTitle(incidentType + ": " + target);
        incident.setIncidentType(incidentType);
        incident.setAmount(amount);
        incident.setAssetSymbol("ETH");
        incident.setSeverity(severity);
        incident.setStatus("NEW");
        incident.setTxHash("0x" + UUID.randomUUID().toString().replace("-", ""));
        incident.setFromAddress("0x" + UUID.randomUUID().toString().replace("-", ""));
        incident.setChainName("Ethereum");
        incident.setPair("ETH/USDT");
        incident.setRiskScore(riskScore);
        incident.setExchangeTag(exchangeTag);
        incident.setCriticalExchange(isExchange);
        incident.setDescription(String.format(
            "%.1f ETH — %s detected (risk: %d/100)", amount, incidentType, riskScore
        ));

        return incident;
    }

    private Object calculateLiveMetrics(Web3Incident latest) {
        // 프론트엔드 게이지를 움직이게 할 동적 데이터 구조
        return java.util.Map.of(
            "overallRisk", latest.getRiskScore(),
            "whaleActivity", 80 + random.nextInt(20),
            "liquidityDepth", 30 + random.nextInt(40),
            "marketSentiment", "NEGATIVE"
        );
    }
}