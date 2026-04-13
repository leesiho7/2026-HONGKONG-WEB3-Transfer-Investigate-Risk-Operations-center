package com.tem.quant.service;

import com.tem.quant.entity.WhaleTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 고래 트랜잭션 의미 분류 엔진 — Arkham Intelligence 기반
 *
 * 분류 우선순위:
 *   1. 하드코딩 믹서 주소 (Tornado Cash, Sinbad, Blender)
 *   2. Arkham 엔티티 타입: darknet / sanctioned → mixer → otc → cex → ...
 *   3. 패턴 기반 OTC 추정 (5000 ETH 이상, 비거래소 양방)
 *
 * 분류 결과 (Category):
 *   EXCHANGE_INFLOW   - 고래 → CEX (매도 압력 시그널)
 *   EXCHANGE_OUTFLOW  - CEX  → 고래 (매수/축적 시그널)
 *   OTC_TRADE         - 장외거래 (OTC 데스크 경유)
 *   MIXER_DEPOSIT     - 믹서 입금 (자금세탁 고위험)
 *   MIXER_WITHDRAWAL  - 믹서 출금 (세탁 완료 의심)
 *   WHALE_MOVEMENT    - 고래 내부 이동 (콜드→핫 등)
 *   DEFI_INTERACTION  - DeFi 프로토콜 상호작용
 *   BRIDGE_TRANSFER   - 크로스체인 브릿지
 *   DARKNET           - 다크넷/OFAC 제재 주소 연루
 *   UNKNOWN           - 분류 불가
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionClassifier {

    private final ArkhamApiService arkhamApiService;

    // ── 분류 카테고리 ─────────────────────────────────────────────────────────

    public enum Category {
        EXCHANGE_INFLOW,
        EXCHANGE_OUTFLOW,
        OTC_TRADE,
        MIXER_DEPOSIT,
        MIXER_WITHDRAWAL,
        WHALE_MOVEMENT,
        DEFI_INTERACTION,
        BRIDGE_TRANSFER,
        DARKNET,
        UNKNOWN;

        /** UI 표시 레이블 */
        public String getDisplayName() {
            return switch (this) {
                case EXCHANGE_INFLOW   -> "Exchange Inflow";
                case EXCHANGE_OUTFLOW  -> "Exchange Outflow";
                case OTC_TRADE         -> "OTC Trade";
                case MIXER_DEPOSIT     -> "Mixer Deposit";
                case MIXER_WITHDRAWAL  -> "Mixer Withdrawal";
                case WHALE_MOVEMENT    -> "Whale Movement";
                case DEFI_INTERACTION  -> "DeFi Interaction";
                case BRIDGE_TRANSFER   -> "Bridge Transfer";
                case DARKNET           -> "Darknet Activity";
                case UNKNOWN           -> "Unknown";
            };
        }

        /**
         * 카테고리 기반 위험 가중치 (0–5)
         * WhaleFilterService.calculateRiskLevel()에서 라벨 가중치와 합산됩니다.
         */
        public int getRiskWeight() {
            return switch (this) {
                case DARKNET           -> 5;
                case MIXER_DEPOSIT     -> 4;
                case MIXER_WITHDRAWAL  -> 4;
                case EXCHANGE_INFLOW   -> 1;
                case OTC_TRADE         -> 1;
                default                -> 0;
            };
        }

        /** CSS badge 클래스 */
        public String getCssClass() {
            return switch (this) {
                case MIXER_DEPOSIT, MIXER_WITHDRAWAL, DARKNET -> "badge-critical";
                case OTC_TRADE                                 -> "badge-otc";
                case EXCHANGE_INFLOW                           -> "badge-exchange";
                case EXCHANGE_OUTFLOW                          -> "badge-outflow";
                case WHALE_MOVEMENT                            -> "badge-whale";
                default                                        -> "badge-default";
            };
        }

        /** 인시던트 타입 문자열 (Web3Incident.incidentType 호환) */
        public String toIncidentType() {
            return switch (this) {
                case EXCHANGE_INFLOW   -> "Unusual Exchange Inflow";
                case EXCHANGE_OUTFLOW  -> "Exchange Outflow";
                case OTC_TRADE         -> "OTC Trade";
                case MIXER_DEPOSIT     -> "Mixer Deposit";
                case MIXER_WITHDRAWAL  -> "Mixer Withdrawal";
                case WHALE_MOVEMENT    -> "Massive Whale Transfer";
                case DEFI_INTERACTION  -> "DeFi Interaction";
                case BRIDGE_TRANSFER   -> "Bridge Transfer";
                case DARKNET           -> "Darknet Activity";
                default                -> "Unknown Activity";
            };
        }
    }

    // ── 알려진 믹서/제재 주소 (Tornado Cash, Sinbad, Blender) ─────────────────

    private static final Set<String> KNOWN_MIXER_ADDRESSES = Set.of(
        // Tornado Cash — 주요 라우터/프록시 (OFAC 제재 2022.08)
        "0xd4b88df4d29f5cedd6857912842cff3b20c8cfa3",
        "0x722122df12d4e14e13ac3b6895a86e84145b6967",
        "0xdd4c48c0b24039969fc16d1cdf626eab821d3384",
        "0xd96f2b1c14db8458374d9aca76e26c3950683b87",
        "0x4736dcf1b7a3d580672cce6e7c65cd5cc9cfba9d",
        "0xd47438c816c9e7f2e2888798db4e0d3c2e22a79f",
        "0x910cbd523d972eb0a6f4cae4618ad62622b39dbf",
        "0xa160cdab225685da1d56aa342ad8841c3b53f291",
        "0xce3c6a7b4b56fb3f85cff9d4cbc49b6a92c86a00",
        // Sinbad.io (OFAC 제재 2023.11)
        "0x7f367cc41522ce07553e823bf3be79a889debe1b",
        // Blender.io (OFAC 제재 2022.05)
        "0x8576acc5c05d6ce88f4e49bf65bdf0c62f91353c"
    );

    // ── Arkham 엔티티 타입 집합 ────────────────────────────────────────────────

    private static final Set<String> MIXER_TYPES   = Set.of("mixer", "tumbler", "privacy");
    private static final Set<String> DARKNET_TYPES  = Set.of("darknet", "sanctioned", "scam", "hack", "phishing");
    private static final Set<String> OTC_TYPES      = Set.of("otc", "over-the-counter");
    private static final Set<String> EXCHANGE_TYPES = Set.of("cex", "exchange");
    private static final Set<String> DEFI_TYPES     = Set.of("defi", "dex");
    private static final Set<String> BRIDGE_TYPES   = Set.of("bridge", "cross-chain");

    // OTC 패턴 임계값: 비거래소 간 이 금액 이상이면 OTC 의심
    private static final double OTC_PATTERN_THRESHOLD_ETH = 5_000.0;

    // ── 분류 메인 로직 ────────────────────────────────────────────────────────

    /**
     * 트랜잭션 분류
     *
     * @param fromAddress 발신 주소
     * @param toAddress   수신 주소
     * @param fromLabelType 기존 OkLink 라벨 타입 (Exchange / Hacker / ...)
     * @param toLabelType   기존 OkLink 라벨 타입
     * @param amount      ETH 금액
     * @return 분류된 Category
     */
    public Category classify(String fromAddress, String toAddress,
                              String fromLabelType, String toLabelType,
                              double amount) {

        String fromLower = fromAddress != null ? fromAddress.toLowerCase() : "";
        String toLower   = toAddress   != null ? toAddress.toLowerCase()   : "";

        // Step 1: 하드코딩 믹서 주소 체크 (API 불필요, 즉시 결정)
        if (KNOWN_MIXER_ADDRESSES.contains(toLower))   return Category.MIXER_DEPOSIT;
        if (KNOWN_MIXER_ADDRESSES.contains(fromLower)) return Category.MIXER_WITHDRAWAL;

        // Step 2: Arkham API로 양쪽 엔티티 조회 (캐시 우선)
        ArkhamApiService.ArkhamAddressResponse fromInfo = safe(arkhamApiService.getAddressIntel(fromAddress));
        ArkhamApiService.ArkhamAddressResponse toInfo   = safe(arkhamApiService.getAddressIntel(toAddress));

        String fromType = fromInfo != null ? fromInfo.getEntityType() : null;
        String toType   = toInfo   != null ? toInfo.getEntityType()   : null;

        log.debug("[Classifier] {} ({}) → {} ({}) | {:.1f} ETH",
            fromInfo != null ? fromInfo.getEntityName() : "Unknown", fromType,
            toInfo   != null ? toInfo.getEntityName()   : "Unknown", toType,
            amount);

        // Step 3: 다크넷/제재 최우선
        if (matchesAny(fromType, DARKNET_TYPES) || matchesAny(toType, DARKNET_TYPES)) {
            return Category.DARKNET;
        }

        // Step 4: 믹서 엔티티 타입
        if (matchesAny(toType,   MIXER_TYPES)) return Category.MIXER_DEPOSIT;
        if (matchesAny(fromType, MIXER_TYPES)) return Category.MIXER_WITHDRAWAL;

        // Step 5: OTC — Arkham 엔티티 기반
        if (matchesAny(fromType, OTC_TYPES) || matchesAny(toType, OTC_TYPES)) {
            return Category.OTC_TRADE;
        }

        // Step 6: 거래소 분류
        boolean fromIsExchange = matchesAny(fromType, EXCHANGE_TYPES) || "Exchange".equals(fromLabelType);
        boolean toIsExchange   = matchesAny(toType,   EXCHANGE_TYPES) || "Exchange".equals(toLabelType);

        if (!fromIsExchange && toIsExchange)  return Category.EXCHANGE_INFLOW;
        if (fromIsExchange  && !toIsExchange) return Category.EXCHANGE_OUTFLOW;

        // Step 7: DeFi
        if (matchesAny(fromType, DEFI_TYPES) || matchesAny(toType, DEFI_TYPES)) {
            return Category.DEFI_INTERACTION;
        }

        // Step 8: Bridge
        if (matchesAny(fromType, BRIDGE_TYPES) || matchesAny(toType, BRIDGE_TYPES)) {
            return Category.BRIDGE_TRANSFER;
        }

        // Step 9: 양쪽 모두 Arkham에서 식별된 엔티티 = 고래 내부 이동
        boolean fromKnown = fromInfo != null && fromInfo.isIdentified();
        boolean toKnown   = toInfo   != null && toInfo.isIdentified();
        if (fromKnown && toKnown) {
            return Category.WHALE_MOVEMENT;
        }

        // Step 10: OTC 패턴 감지 (비거래소 간 대량 이체)
        if (!fromIsExchange && !toIsExchange && amount >= OTC_PATTERN_THRESHOLD_ETH) {
            log.info("[Classifier] OTC 패턴 감지: {:.0f} ETH — 비거래소 간 대량 이체", amount);
            return Category.OTC_TRADE;
        }

        return Category.UNKNOWN;
    }

    /** WhaleTransaction 오버로드 편의 메서드 */
    public Category classify(WhaleTransaction tx) {
        return classify(
            tx.getFromAddress(), tx.getToAddress(),
            tx.getFromLabelType(), tx.getToLabelType(),
            tx.getAmount() != null ? tx.getAmount() : 0.0
        );
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private boolean matchesAny(String type, Set<String> set) {
        return type != null && set.contains(type.toLowerCase());
    }

    /** null-safe 래퍼 */
    private ArkhamApiService.ArkhamAddressResponse safe(ArkhamApiService.ArkhamAddressResponse r) {
        return r;
    }
}
