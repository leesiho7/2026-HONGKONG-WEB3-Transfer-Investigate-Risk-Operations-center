package com.tem.quant.service;

import com.tem.quant.entity.AddressLabel;
import com.tem.quant.repository.AddressLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 지갑 주소 → 라벨/타입 조회 서비스
 *
 * OKLink API 제거 — address_labels DB 테이블 우선 조회,
 * 없으면 알려진 하드코딩 주소 매핑 → 최종 Unknown 반환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OkLinkLabelService {

    private final AddressLabelRepository labelRepository;

    // ── 알려진 주요 거래소/해커 주소 (하드코딩 fallback) ──────────────────
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
        // Lazarus
        Map.entry("0x098b716b8aaf21512996dc57eb0615e2383e2f96", new LabelResult("Lazarus Group",      "Hacker")),
        Map.entry("0xa0e1c89ef1a489c9c7de96311ed5ce5d32c20e4b", new LabelResult("Ronin Bridge Hack",  "Hacker"))
    );

    /**
     * 주소 라벨 조회
     * 1순위: DB (address_labels 테이블)
     * 2순위: 하드코딩 known addresses
     * 3순위: Unknown 반환
     */
    public LabelResult getLabel(String address, String chain) {
        if (address == null || address.isBlank()) {
            return LabelResult.unknown();
        }

        String lowerAddr = address.toLowerCase();

        // 1. DB 조회
        Optional<AddressLabel> dbResult = labelRepository.findByAddress(lowerAddr);
        if (dbResult.isPresent()) {
            AddressLabel al = dbResult.get();
            String type = categoryToType(al.getCategory());
            String label = al.getGroupName()
                + (al.getSubTag() != null && !al.getSubTag().isBlank() ? " " + al.getSubTag() : "");
            log.debug("[Label] DB 조회: {} → {} ({})", lowerAddr.substring(0, Math.min(10, lowerAddr.length())), label, type);
            return new LabelResult(label, type);
        }

        // 2. 하드코딩 fallback
        LabelResult known = KNOWN_ADDRESSES.get(lowerAddr);
        if (known != null) {
            log.debug("[Label] 하드코딩: {} → {}", lowerAddr.substring(0, Math.min(10, lowerAddr.length())), known.label());
            return known;
        }

        return LabelResult.unknown();
    }

    private String categoryToType(String category) {
        if (category == null) return "Unknown";
        return switch (category.toUpperCase()) {
            case "EXCHANGE"   -> "Exchange";
            case "HACKER"     -> "Hacker";
            case "WHALE"      -> "Whale";
            case "DEX", "DEFI" -> "DeFi";
            case "COLDWALLET" -> "ColdWallet";
            default           -> "Identified";
        };
    }

    // ── DTO ──────────────────────────────────────────────────────────────
    public record LabelResult(String label, String type) {
        public static LabelResult unknown() {
            return new LabelResult("Unknown", "Unknown");
        }

        public int riskWeight() {
            return switch (type) {
                case "Hacker"   -> 2;
                case "Exchange" -> 1;
                default         -> 0;
            };
        }
    }
}
