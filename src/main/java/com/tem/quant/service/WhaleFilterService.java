package com.tem.quant.service;

import com.tem.quant.entity.Severity;
import com.tem.quant.entity.Web3Incident;
import com.tem.quant.entity.WhaleTransaction;
import com.tem.quant.repository.Web3IncidentRepository;
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
 * QuickNode 웹훅 수신 → 고래 트랜잭션 필터링 → Arkham 기반 분류 → 인시던트 생성
 *
 * Enterprise 업그레이드 (Arkham API 통합):
 *   - OkLinkLabelService: DB → 하드코딩 → Arkham 4-tier 조회
 *   - TransactionClassifier: OTC / Mixer / Whale / Exchange 정밀 분류
 *   - 위험도 산정: 라벨 가중치 + 카테고리 가중치 복합 판정
 *   - 믹서/다크넷 트랜잭션 → 무조건 CRITICAL + 전용 인시던트 타입
 *
 * 처리 흐름:
 *   QuickNode Webhook → Wei 변환 → 임계값 체크
 *     → OkLinkLabelService (4-tier) → TransactionClassifier (Arkham)
 *     → 위험도 산정 → DB 저장 → WebSocket 브로드캐스트 → 인시던트 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhaleFilterService {

    private final WhaleTransactionRepository repository;
    private final Web3IncidentRepository incidentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OkLinkLabelService labelService;
    private final WhaleWebSocketHandler whaleWebSocketHandler;
    private final TransactionClassifier classifier;

    @Value("${whale.eth.threshold:1000}")
    private double ethThreshold;

    private static final double ETH_PRICE_USD = 2_400.0;

    // ── Ethereum 웹훅 처리 ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void processEthWebhook(Map<String, Object> payload) {
        try {
            Object dataObj = payload.get("data");
            if (dataObj == null) return;

            List<?> dataList = (List<?>) dataObj;
            if (dataList.isEmpty()) return;

            for (Object item : dataList) {
                if (item instanceof List<?> batch) {
                    // QuickNode 실제 포맷: data = [ [tx, tx, ...], ... ]
                    for (Object txObj : batch) {
                        if (txObj instanceof Map<?, ?> tx) {
                            processEthTransaction((Map<String, Object>) tx);
                        }
                    }
                } else if (item instanceof Map<?, ?> tx) {
                    // 플랫 포맷(예비 대응): data = [ tx, tx, ... ]
                    processEthTransaction((Map<String, Object>) tx);
                } else {
                    log.warn("⚠️  알 수 없는 data 항목 타입: {}", item == null ? "null" : item.getClass().getSimpleName());
                }
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

        // Wei → ETH 변환
        String valueHex = getString(tx, "value");
        double ethAmount = convertWeiToEth(valueHex);

        if (ethAmount < ethThreshold) return;

        String from = getString(tx, "from", "fromAddress");
        String to   = getString(tx, "to",   "toAddress");
        Object blockObj = tx.get("blockNumber");
        Long blockNumber = blockObj instanceof Number ? ((Number) blockObj).longValue() : null;

        log.info("[Whale] 탐지: {} ETH | tx={}", String.format("%.1f", ethAmount), txHash);

        // Tier 1~3 라벨 조회 (DB → 하드코딩 → Arkham)
        OkLinkLabelService.LabelResult fromLabel = labelService.getLabel(from, "eth");
        OkLinkLabelService.LabelResult toLabel   = labelService.getLabel(to,   "eth");

        // Arkham 기반 트랜잭션 분류
        TransactionClassifier.Category category = classifier.classify(
            from, to, fromLabel.type(), toLabel.type(), ethAmount
        );

        // 복합 위험도 산정 (라벨 가중치 + 카테고리 가중치)
        String riskLevel = calculateRiskLevel(ethAmount, ethThreshold, fromLabel, toLabel, category);

        // Arkham 엔티티 이름 수집 (fromLabel / toLabel에 이미 반영됨)
        String arkhamEntityFrom = fromLabel.label().equals("Unknown") ? null : fromLabel.label();
        String arkhamEntityTo   = toLabel.label().equals("Unknown")   ? null : toLabel.label();

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
        whale.setTransactionCategory(category.name());
        whale.setArkhamEntityFrom(arkhamEntityFrom);
        whale.setArkhamEntityTo(arkhamEntityTo);
        whale.setRiskLevel(riskLevel);
        whale.setBlockNumber(blockNumber);

        WhaleTransaction saved = repository.save(whale);
        broadcast(saved);
        createIncident(saved, category);
    }

    // ── 위험도 산정 ───────────────────────────────────────────────────────────

    /**
     * 복합 위험도 산정
     *
     * 우선순위:
     *   1. 믹서/다크넷 카테고리 → 무조건 CRITICAL
     *   2. 라벨 가중치 합산 (Hacker=2, Mixer=3, Darknet=4, Exchange=1)
     *   3. 카테고리 가중치 (MIXER=4, DARKNET=5, OTC=1, EXCHANGE_INFLOW=1)
     *   4. 금액 기반 판정 (라벨 미식별 환경 대응)
     */
    private String calculateRiskLevel(double amount, double threshold,
                                       OkLinkLabelService.LabelResult from,
                                       OkLinkLabelService.LabelResult to,
                                       TransactionClassifier.Category category) {

        // 믹서/다크넷은 금액 무관 CRITICAL
        if (category == TransactionClassifier.Category.MIXER_DEPOSIT   ||
            category == TransactionClassifier.Category.MIXER_WITHDRAWAL ||
            category == TransactionClassifier.Category.DARKNET) {
            return "CRITICAL";
        }

        int labelWeight    = from.riskWeight() + to.riskWeight();
        int categoryWeight = category.getRiskWeight();
        int totalWeight    = labelWeight + categoryWeight;

        if (totalWeight >= 4) return "CRITICAL";
        if (totalWeight >= 2) return "HIGH";
        if (totalWeight >= 1) return "HIGH";

        // 금액 기반 (라벨/분류 없을 때)
        if (amount >= threshold * 5) return "CRITICAL";
        if (amount >= threshold * 2) return "HIGH";
        if (amount >= threshold)     return "MEDIUM";

        return "INFO";
    }

    // ── WebSocket 브로드캐스트 ─────────────────────────────────────────────────

    private void broadcast(WhaleTransaction tx) {
        whaleWebSocketHandler.broadcastWhaleEvent(tx);
        log.info("[Whale] 브로드캐스트: {} ETH | {} → {} | Cat={} | Risk={}",
            String.format("%.1f", tx.getAmount()),
            tx.getFromLabel(), tx.getToLabel(),
            tx.getTransactionCategory(), tx.getRiskLevel());
    }

    // ── 인시던트 생성 ──────────────────────────────────────────────────────────

    /**
     * HIGH / CRITICAL 트랜잭션 → Web3Incident 생성 + ROC 대시보드 알림
     */
    private void createIncident(WhaleTransaction tx, TransactionClassifier.Category category) {
        if ("INFO".equals(tx.getRiskLevel()) || "MEDIUM".equals(tx.getRiskLevel())) return;
        if (incidentRepository.findByTxHash(tx.getTxHash()).isPresent()) return;

        String incidentType = category.toIncidentType();

        boolean isCriticalExchange = tx.getToLabel() != null &&
            (tx.getToLabel().toLowerCase().contains("binance") ||
             tx.getToLabel().toLowerCase().contains("okx")     ||
             tx.getToLabel().toLowerCase().contains("hashkey") ||
             tx.getToLabel().toLowerCase().contains("coinbase"));

        Web3Incident incident = new Web3Incident();
        incident.setTxHash(tx.getTxHash());
        incident.setTitle(buildTitle(tx, category));
        incident.setDescription(buildDescription(tx, category));
        incident.setSeverity(Severity.valueOf(tx.getRiskLevel()));
        incident.setStatus("NEW");
        incident.setAmount(tx.getAmount());
        incident.setAssetSymbol(tx.getAssetSymbol());
        incident.setFromAddress(tx.getFromAddress());
        incident.setToAddress(tx.getToAddress());
        incident.setChainName(tx.getChainName());
        incident.setIncidentType(incidentType);
        incident.setTransactionCategory(category.name());
        incident.setArkhamEntityFrom(tx.getArkhamEntityFrom());
        incident.setArkhamEntityTo(tx.getArkhamEntityTo());
        incident.setExchangeTag(tx.getToLabel());
        incident.setCriticalExchange(isCriticalExchange);
        incident.setRiskScore(riskScore(tx.getRiskLevel(), category));
        incident.setPair("ETH/USDT");

        Web3Incident saved = incidentRepository.save(incident);
        messagingTemplate.convertAndSend("/topic/alerts", saved);
        log.info("[ROC] 인시던트 생성: {} | {} | {}", saved.getCaseId(), incidentType, saved.getTitle());
    }

    private String buildTitle(WhaleTransaction tx, TransactionClassifier.Category category) {
        String target = tx.getToLabel() != null && !tx.getToLabel().equals("Unknown")
            ? tx.getToLabel()
            : (tx.getFromLabel() != null && !tx.getFromLabel().equals("Unknown") ? tx.getFromLabel() : "Unknown");

        return switch (category) {
            case MIXER_DEPOSIT     -> "Mixer Deposit Detected: " + target;
            case MIXER_WITHDRAWAL  -> "Mixer Withdrawal Detected: " + target;
            case DARKNET           -> "Darknet Activity Alert: " + target;
            case OTC_TRADE         -> "OTC Trade Detected: " + String.format("%.0f ETH", tx.getAmount());
            case EXCHANGE_INFLOW   -> "Unusual Exchange Inflow: " + target;
            case EXCHANGE_OUTFLOW  -> "Exchange Outflow: " + target;
            case WHALE_MOVEMENT    -> "Whale Movement: " + String.format("%.0f ETH", tx.getAmount());
            default                -> "Massive Whale Transfer: " + target;
        };
    }

    private String buildDescription(WhaleTransaction tx, TransactionClassifier.Category category) {
        return String.format(
            "[%s] %.1f ETH ($%.1fM) | %s → %s | Risk: %s | Arkham: %s → %s",
            category.getDisplayName(),
            tx.getAmount(),
            tx.getAmount() * ETH_PRICE_USD / 1_000_000,
            tx.getFromLabel() != null ? tx.getFromLabel() : "Unknown",
            tx.getToLabel()   != null ? tx.getToLabel()   : "Unknown",
            tx.getRiskLevel(),
            tx.getArkhamEntityFrom() != null ? tx.getArkhamEntityFrom() : "Unidentified",
            tx.getArkhamEntityTo()   != null ? tx.getArkhamEntityTo()   : "Unidentified"
        );
    }

    private int riskScore(String riskLevel, TransactionClassifier.Category category) {
        int base = switch (riskLevel) {
            case "CRITICAL" -> 90;
            case "HIGH"     -> 70;
            default         -> 50;
        };
        // 카테고리 가중치로 최종 스코어 보정 (최대 99)
        int bonus = category.getRiskWeight() * 2;
        return Math.min(99, base + bonus + (int)(Math.random() * 5));
    }

    // ── 유틸리티 ───────────────────────────────────────────────────────────────

    private double convertWeiToEth(String hexWei) {
        if (hexWei == null || hexWei.equals("0x0") || hexWei.isBlank()) return 0.0;
        try {
            String clean = hexWei.startsWith("0x") ? hexWei.substring(2) : hexWei;
            BigInteger wei = new BigInteger(clean, 16);
            return new BigDecimal(wei)
                .divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.DOWN)
                .doubleValue();
        } catch (Exception e) {
            try { return Double.parseDouble(hexWei) / 1e18; } catch (Exception ex) { return 0.0; }
        }
    }

    private String getString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }
}
