package com.tem.quant.service;

import com.tem.quant.entity.AddressLabel;
import com.tem.quant.repository.AddressLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 지갑 주소 → 라벨/타입 조회 서비스 (Enterprise Edition)
 *
 * 4-tier 조회 순서:
 *   1. DB (address_labels 테이블)
 *   2. 하드코딩 known addresses (즉시 응답)
 *   3. Arkham Intelligence API (엔터프라이즈 인텔리전스)
 *   4. Unknown 반환
 *
 * Arkham 결과는 DB에 자동 저장(학습)되어 이후 조회 시 Tier 1에서 응답합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OkLinkLabelService {

    private final AddressLabelRepository labelRepository;
    private final ArkhamApiService arkhamApiService;

    // ── 알려진 주요 거래소/해커 주소 (하드코딩 fallback) ──────────────────────
    private static final Map<String, LabelResult> KNOWN_ADDRESSES = Map.ofEntries(
        // Binance
        Map.entry("0x28c6c06298d514db089934071355e5743bf21d60", new LabelResult("Binance Hot Wallet",  "Exchange")),
        Map.entry("0xbe0eb53f46cd790cd13851d5eff43d12404d33e8", new LabelResult("Binance Cold Wallet", "Exchange")),
        Map.entry("0xf977814e90da44bfa03b6295a0616a897441acec", new LabelResult("Binance Exchange",    "Exchange")),
        // OKX
        Map.entry("0x6cc5f688a315f3dc28a7781717a9a798a59fda7b", new LabelResult("OKX Hot Wallet",     "Exchange")),
        Map.entry("0x98ec059dc8ad2e1d83a1b069b8d37bf2c1e3e2ca", new LabelResult("OKX Deposit",        "Exchange")),
        // Coinbase
        Map.entry("0x71660c4005ba85c37ccec55d0c4493e66fe775d3", new LabelResult("Coinbase",           "Exchange")),
        Map.entry("0xa090e606e30bd747d4e6245a1517ebe430f0057e", new LabelResult("Coinbase Prime",     "Exchange")),
        // Kraken
        Map.entry("0x2910543af39aba0cd09dbb2d50200b3e800a63d2", new LabelResult("Kraken Exchange",    "Exchange")),
        // Vitalik
        Map.entry("0xd8da6bf26964af9d7eed9e03e53415d37aa96045", new LabelResult("Vitalik Buterin",   "Whale")),
        // Lazarus Group
        Map.entry("0x098b716b8aaf21512996dc57eb0615e2383e2f96", new LabelResult("Lazarus Group",      "Hacker")),
        Map.entry("0xa0e1c89ef1a489c9c7de96311ed5ce5d32c20e4b", new LabelResult("Ronin Bridge Hack",  "Hacker"))
    );

    // ── 주소 라벨 조회 ────────────────────────────────────────────────────────

    /**
     * 주소 라벨 조회
     *
     * @param address Ethereum 주소
     * @param chain   체인 식별자 (현재 "eth" 전용)
     * @return LabelResult (label, type, riskWeight)
     */
    public LabelResult getLabel(String address, String chain) {
        if (address == null || address.isBlank()) {
            return LabelResult.unknown();
        }

        String lowerAddr = address.toLowerCase();

        // Tier 1: DB 조회
        Optional<AddressLabel> dbResult = labelRepository.findByAddress(lowerAddr);
        if (dbResult.isPresent()) {
            AddressLabel al = dbResult.get();
            String type  = categoryToType(al.getCategory());
            String label = al.getGroupName()
                + (al.getSubTag() != null && !al.getSubTag().isBlank() ? " " + al.getSubTag() : "");
            log.debug("[Label-T1] DB: {} → {} ({})", abbrev(lowerAddr), label, type);
            return new LabelResult(label, type);
        }

        // Tier 2: 하드코딩 known addresses
        LabelResult known = KNOWN_ADDRESSES.get(lowerAddr);
        if (known != null) {
            log.debug("[Label-T2] 하드코딩: {} → {}", abbrev(lowerAddr), known.label());
            return known;
        }

        // Tier 3: Arkham Intelligence API
        LabelResult arkhamResult = queryArkham(lowerAddr);
        if (arkhamResult != null) {
            persistArkhamResult(lowerAddr, arkhamResult); // DB 자동 저장
            return arkhamResult;
        }

        return LabelResult.unknown();
    }

    // ── Arkham 조회 및 타입 변환 ──────────────────────────────────────────────

    private LabelResult queryArkham(String address) {
        try {
            ArkhamApiService.ArkhamAddressResponse resp = arkhamApiService.getAddressIntel(address);
            if (resp == null || !resp.isIdentified()) return null;

            String entityName = resp.getEntityName();
            String entityType = resp.getEntityType();
            String mappedType = arkhamTypeToLabelType(entityType);

            log.info("[Label-T3] Arkham: {} → {} ({})", abbrev(address), entityName, mappedType);
            return new LabelResult(entityName, mappedType);

        } catch (Exception e) {
            log.warn("[Label-T3] Arkham 조회 실패 {}: {}", abbrev(address), e.getMessage());
            return null;
        }
    }

    /**
     * Arkham 엔티티 타입 → 내부 타입 문자열 변환
     *
     * Arkham 타입: cex | otc | mixer | defi | miner | bridge | darknet | sanctioned
     */
    private String arkhamTypeToLabelType(String arkhamType) {
        if (arkhamType == null) return "Identified";
        return switch (arkhamType.toLowerCase()) {
            case "cex", "exchange"    -> "Exchange";
            case "otc"                -> "OTC";
            case "mixer", "tumbler"   -> "Mixer";
            case "defi", "dex"        -> "DeFi";
            case "miner"              -> "Miner";
            case "bridge"             -> "Bridge";
            case "darknet"            -> "Darknet";
            case "sanctioned"         -> "Sanctioned";
            case "hack", "scam"       -> "Hacker";
            default                   -> "Identified";
        };
    }

    /**
     * Arkham 조회 결과를 DB에 저장하여 이후 Tier 1에서 응답
     */
    private void persistArkhamResult(String address, LabelResult result) {
        try {
            if (labelRepository.findByAddress(address).isPresent()) return; // 이미 있음

            AddressLabel al = new AddressLabel();
            al.setAddress(address);
            al.setGroupName(result.label());
            al.setCategory(result.type().toUpperCase());
            al.setSource("Arkham");
            al.setCreatedAt(LocalDateTime.now());
            labelRepository.save(al);
            log.debug("[Label] Arkham 결과 DB 저장: {} → {}", abbrev(address), result.label());
        } catch (Exception e) {
            log.warn("[Label] DB 저장 실패 {}: {}", abbrev(address), e.getMessage());
        }
    }

    // ── 레거시 카테고리 변환 ──────────────────────────────────────────────────

    private String categoryToType(String category) {
        if (category == null) return "Unknown";
        return switch (category.toUpperCase()) {
            case "EXCHANGE"            -> "Exchange";
            case "HACKER"              -> "Hacker";
            case "WHALE"               -> "Whale";
            case "DEX", "DEFI"         -> "DeFi";
            case "COLDWALLET"          -> "ColdWallet";
            case "OTC"                 -> "OTC";
            case "MIXER"               -> "Mixer";
            case "MINER"               -> "Miner";
            case "BRIDGE"              -> "Bridge";
            case "DARKNET"             -> "Darknet";
            case "SANCTIONED"          -> "Sanctioned";
            default                    -> "Identified";
        };
    }

    private String abbrev(String addr) {
        return addr != null && addr.length() >= 10 ? addr.substring(0, 10) : addr;
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    public record LabelResult(String label, String type) {

        public static LabelResult unknown() {
            return new LabelResult("Unknown", "Unknown");
        }

        /**
         * 위험 가중치 (WhaleFilterService.calculateRiskLevel 에서 사용)
         * Mixer / Sanctioned / Darknet 추가
         */
        public int riskWeight() {
            return switch (type) {
                case "Darknet", "Sanctioned" -> 4;
                case "Mixer"                 -> 3;
                case "Hacker"                -> 2;
                case "Exchange"              -> 1;
                default                      -> 0;
            };
        }
    }
}
