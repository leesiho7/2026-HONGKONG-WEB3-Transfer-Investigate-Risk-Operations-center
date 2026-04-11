package com.tem.quant.service;

import com.tem.quant.entity.WhaleTransaction;
import com.tem.quant.repository.WhaleTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * QuickNode 웹훅 페이로드를 수신하고 고래 트랜잭션을 필터링합니다.
 *
 * 필터 임계값:
 *   - ETH: 1,000 ETH 이상
 *   - BTC: 100 BTC 이상
 *
 * 처리 흐름:
 *   1. 웹훅 JSON 파싱
 *   2. 체인별 금액 변환 (Wei → ETH / Satoshi → BTC)
 *   3. 임계값 비교
 *   4. OKLink 주소 라벨 조회
 *   5. 위험도 산정
 *   6. DB 저장 + WebSocket 즉시 브로드캐스트
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhaleFilterService {

    private final WhaleTransactionRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OkLinkLabelService labelService;
    private final WhaleWebSocketHandler whaleWebSocketHandler;

    // ETH 임계값 (application.properties: whale.eth.threshold)
    @Value("${whale.eth.threshold:1000}")
    private double ethThreshold;

    private static final double ETH_PRICE_USD = 2_400.0;

    // ── 이더리움 웹훅 처리 ──────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public void processEthWebhook(Map<String, Object> payload) {
        try {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) payload.get("data");
            if (dataList == null || dataList.isEmpty()) return;

            for (Map<String, Object> tx : dataList) {
                processEthTransaction(tx);
            }
        } catch (Exception e) {
            log.error("ETH 웹훅 처리 실패: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processEthTransaction(Map<String, Object> tx) {
        String txHash = getString(tx, "hash", "txHash");
        if (txHash == null) return;
        if (repository.findByTxHash(txHash).isPresent()) return; // 중복 방지

        // 금액 변환: hex wei → ETH
        String valueHex = getString(tx, "value");
        double ethAmount = convertWeiToEth(valueHex);

        // 임계값 필터
        if (ethAmount < ethThreshold) return;

        String from = getString(tx, "from", "fromAddress");
        String to   = getString(tx, "to",   "toAddress");
        Object blockObj = tx.get("blockNumber");
        Long blockNumber = blockObj instanceof Number ? ((Number) blockObj).longValue() : null;

        log.info("🐋 ETH 고래 탐지: {} ETH | tx={}", String.format("%.1f", ethAmount), txHash);

        // OKLink 라벨 조회
        OkLinkLabelService.LabelResult fromLabel = labelService.getLabel(from, "eth");
        OkLinkLabelService.LabelResult toLabel   = labelService.getLabel(to,   "eth");

        // 위험도 산정
        String riskLevel = calculateRiskLevel(ethAmount, ethThreshold, fromLabel, toLabel);

        WhaleTransaction whale = new WhaleTransaction();
        whale.setTxHash(txHash);
        whale.setChainName("Ethereum");
        whale.setAssetSymbol("ETH");
        whale.setAmount(ethAmount);
        whale.setAmountUsd(ethAmount * ETH_PRICE_USD);
        whale.setFromAddress(from);
        whale.setToAddress(to);
        whale.setFromLabel(fromLabel.label());
        whale.setToLabel(toLabel.label());
        whale.setFromLabelType(fromLabel.type());
        whale.setToLabelType(toLabel.type());
        whale.setRiskLevel(riskLevel);
        whale.setBlockNumber(blockNumber);

        WhaleTransaction saved = repository.save(whale);
        broadcast(saved);
    }

    // BTC 지원 제거 — Ethereum Mainnet 전용으로 운영

    // ── 유틸리티 ────────────────────────────────────────────────────────

    /** Hex Wei 문자열을 ETH 단위로 변환 */
    private double convertWeiToEth(String hexWei) {
        if (hexWei == null || hexWei.equals("0x0") || hexWei.isBlank()) return 0.0;
        try {
            String clean = hexWei.startsWith("0x") ? hexWei.substring(2) : hexWei;
            BigInteger wei = new BigInteger(clean, 16);
            return new BigDecimal(wei)
                .divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.DOWN)
                .doubleValue();
        } catch (Exception e) {
            // 십진수 문자열일 수도 있음
            try { return Double.parseDouble(hexWei) / 1e18; } catch (Exception ex) { return 0.0; }
        }
    }

    /** 위험도 등급 산정 */
    private String calculateRiskLevel(double amount, double threshold,
                                       OkLinkLabelService.LabelResult from,
                                       OkLinkLabelService.LabelResult to) {
        int weight = from.riskWeight() + to.riskWeight();
        if (weight >= 2) return "CRITICAL";
        if (weight == 1 || amount >= threshold * 10) return "HIGH";
        if (amount >= threshold * 3) return "MEDIUM";
        return "INFO";
    }

    /** WebSocket 브로드캐스트 — 지도 시각화 포맷으로 전송 */
    private void broadcast(WhaleTransaction tx) {
        whaleWebSocketHandler.broadcastWhaleEvent(tx);
        log.info("📡 WebSocket 전송: {} {} | {} → {} | Risk={}",
            tx.getAmount(), tx.getAssetSymbol(), tx.getFromLabel(), tx.getToLabel(), tx.getRiskLevel());
    }

    /** Map에서 여러 키 중 첫 번째로 찾은 값 반환 */
    private String getString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

}
