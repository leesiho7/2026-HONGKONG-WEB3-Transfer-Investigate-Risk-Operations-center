package com.tem.quant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OKLink API를 통해 지갑 주소의 엔티티 라벨을 조회합니다.
 * 결과는 인메모리 캐시에 저장하여 API 호출 횟수를 최소화합니다.
 *
 * 지원 라벨 타입: Exchange | Hacker | ColdWallet | Whale | DeFi | Unknown
 */
@Service
@Slf4j
public class OkLinkLabelService {

    private final WebClient webClient;

    @Value("${oklink.api.key:}")   // 미설정 시 빈 문자열 — 앱 기동은 정상, API 호출 시 401 후 Unknown 반환
    private String apiKey;

    // 주소 → 라벨 캐시 (프로세스 재시작 전까지 유지)
    private final Map<String, LabelResult> labelCache = new ConcurrentHashMap<>();

    // 알려진 해킹 관련 키워드
    private static final List<String> HACKER_KEYWORDS = List.of(
        "hack", "exploit", "scam", "phish", "drain", "rug", "stolen", "ronin", "lazarus"
    );
    // 거래소 키워드
    private static final List<String> EXCHANGE_KEYWORDS = List.of(
        "binance", "okx", "okex", "coinbase", "kraken", "bybit", "huobi", "htx",
        "kucoin", "gate", "bitfinex", "hashkey", "upbit"
    );
    // 콜드 월렛 키워드
    private static final List<String> COLD_KEYWORDS = List.of(
        "cold", "treasury", "foundation", "reserve", "custody", "multisig"
    );

    public OkLinkLabelService() {
        this.webClient = WebClient.builder()
            .baseUrl("https://www.oklink.com")
            .build();
    }

    /**
     * 주소의 라벨을 조회합니다. 캐시된 결과가 있으면 즉시 반환합니다.
     *
     * @param address  지갑 주소 (0x... 또는 BTC 주소)
     * @param chain    체인 (eth / btc)
     * @return LabelResult (라벨명 + 타입)
     */
    public LabelResult getLabel(String address, String chain) {
        if (address == null || address.isBlank()) {
            return LabelResult.unknown();
        }

        String cacheKey = chain + ":" + address.toLowerCase();
        if (labelCache.containsKey(cacheKey)) {
            return labelCache.get(cacheKey);
        }

        LabelResult result = fetchLabelFromApi(address, chain);
        labelCache.put(cacheKey, result);
        return result;
    }

    /** OKLink /address/entity-label API 호출 */
    @SuppressWarnings("unchecked")
    private LabelResult fetchLabelFromApi(String address, String chain) {
        try {
            Map<String, Object> response = webClient.get()
                .uri(ub -> ub
                    .path("/api/v5/explorer/address/entity-label")
                    .queryParam("chainShortName", chain)
                    .queryParam("address", address)
                    .build())
                .header("Ok-Access-Key", apiKey)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))   // OKLink 응답 최대 5초 대기
                .block();

            if (response == null) return LabelResult.unknown();

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) return LabelResult.unknown();

            Map<String, Object> item = data.get(0);
            String label = (String) item.getOrDefault("label", "");
            String entity = (String) item.getOrDefault("entity", "");
            String display = label.isBlank() ? entity : label;

            if (display.isBlank()) return LabelResult.unknown();

            String type = classifyLabel(display.toLowerCase());
            log.info("  OKLink 라벨: {} → {} ({})", address.substring(0, Math.min(10, address.length())), display, type);
            return new LabelResult(display, type);

        } catch (Exception e) {
            log.warn("  OKLink 라벨 조회 실패 ({}): {}", address, e.getMessage());
            return LabelResult.unknown();
        }
    }

    /** 라벨 문자열로부터 타입을 분류 */
    private String classifyLabel(String label) {
        if (HACKER_KEYWORDS.stream().anyMatch(label::contains)) return "Hacker";
        if (EXCHANGE_KEYWORDS.stream().anyMatch(label::contains))  return "Exchange";
        if (COLD_KEYWORDS.stream().anyMatch(label::contains))      return "ColdWallet";
        if (label.contains("defi") || label.contains("protocol") || label.contains("dao")) return "DeFi";
        if (label.contains("whale") || label.contains("fund"))     return "Whale";
        return "Identified";
    }

    // ── DTO ─────────────────────────────────────────────────────────────────
    public record LabelResult(String label, String type) {
        public static LabelResult unknown() {
            return new LabelResult("Unknown", "Unknown");
        }

        /** 라벨 타입에 따른 위험도 기여 점수 (0=없음, 2=CRITICAL) */
        public int riskWeight() {
            return switch (type) {
                case "Hacker"   -> 2;
                case "Exchange" -> 1;
                default         -> 0;
            };
        }
    }
}
