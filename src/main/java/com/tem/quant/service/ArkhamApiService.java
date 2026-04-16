package com.tem.quant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Arkham Intelligence API 클라이언트
 *
 * 주요 기능:
 *   - 주소 엔티티 식별: Exchange / OTC / Mixer / Darknet / Sanctioned / DeFi / Bridge
 *   - Caffeine 캐시 10분 TTL
 *   - 429 Rate Limit 자동 재시도 (최대 2회, 2초 간격)
 *   - 5초 읽기 타임아웃으로 파이프라인 블로킹 방지
 *
 * 변경 이력:
 *   - WebClient(WebFlux) → RestClient(Web MVC) 교체: Reactor+Netty 스택 제거로 ~100MB 절감
 */
@Service
@Slf4j
public class ArkhamApiService {

    @Value("${arkham.api.key}")
    private String apiKey;

    @Value("${arkham.api.base-url}")
    private String baseUrl;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl.trim())
                .defaultHeader("API-Key", apiKey.trim())
                .build();

        log.info("[Arkham] API 클라이언트 초기화 완료 — baseUrl={}", baseUrl.trim());
    }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArkhamAddressResponse {

        @JsonProperty("address")
        private String address;

        @JsonProperty("chain")
        private String chain;

        @JsonProperty("isContract")
        private Boolean isContract;

        @JsonProperty("arkhamEntity")
        private ArkhamEntity arkhamEntity;

        @JsonProperty("arkhamLabel")
        private ArkhamLabel arkhamLabel;

        /** 엔티티 이름 반환 — entity → label → null 순서 */
        public String getEntityName() {
            if (arkhamEntity != null && arkhamEntity.getName() != null) return arkhamEntity.getName();
            if (arkhamLabel  != null && arkhamLabel.getName()  != null) return arkhamLabel.getName();
            return null;
        }

        /**
         * 엔티티 타입 소문자 반환
         * Arkham 타입: cex | otc | mixer | defi | miner | bridge | darknet | sanctioned
         */
        public String getEntityType() {
            if (arkhamEntity != null && arkhamEntity.getType() != null)
                return arkhamEntity.getType().toLowerCase();
            if (arkhamLabel != null && arkhamLabel.getType() != null)
                return arkhamLabel.getType().toLowerCase();
            return null;
        }

        /** 식별된 엔티티가 있으면 true */
        public boolean isIdentified() {
            return getEntityName() != null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArkhamEntity {
        private String id;
        private String name;
        /** cex | otc | mixer | defi | miner | bridge | darknet | sanctioned */
        private String type;
        private String website;
        private String twitter;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArkhamLabel {
        private String name;
        private String type;
    }

    // ── API Methods ───────────────────────────────────────────────────────────

    /**
     * 주소 엔티티 정보 조회 (캐시 우선)
     *
     * 429 수신 시 최대 2회 재시도 (2초 간격), 404는 null 반환.
     */
    @Cacheable(value = "arkhamAddress", key = "#address.toLowerCase()", unless = "#result == null")
    public ArkhamAddressResponse getAddressIntel(String address) {
        if (address == null || address.isBlank()) return null;

        String normalized = address.toLowerCase();
        log.debug("[Arkham] 주소 조회: {}", normalized.substring(0, Math.min(10, normalized.length())));

        for (int attempt = 0; attempt <= 2; attempt++) {
            try {
                ArkhamAddressResponse resp = restClient.get()
                        .uri("/addresses/{address}", normalized)
                        .retrieve()
                        .body(ArkhamAddressResponse.class);

                if (resp != null && resp.isIdentified()) {
                    log.info("[Arkham] 식별 성공: {} → {} ({})",
                            normalized.substring(0, Math.min(10, normalized.length())),
                            resp.getEntityName(), resp.getEntityType());
                }
                return resp;

            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 429 && attempt < 2) {
                    log.warn("[Arkham] Rate limit — {}초 후 재시도 ({}/2)", 2, attempt + 1);
                    try { Thread.sleep(2_000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else if (status == 404) {
                    return null; // 주소 정보 없음 — 정상 케이스
                } else {
                    log.warn("[Arkham] HTTP {} — {}", status,
                            normalized.substring(0, Math.min(10, normalized.length())));
                    return null;
                }
            } catch (Exception e) {
                log.warn("[Arkham] 조회 실패 {}: {}", normalized.substring(0, Math.min(10, normalized.length())), e.getMessage());
                return null;
            }
        }
        return null;
    }

    /** 기존 호환성 유지 — 엔티티 이름 문자열 반환 */
    public String getAddressLabel(String address) {
        ArkhamAddressResponse resp = getAddressIntel(address);
        if (resp == null) return "Unknown";
        String name = resp.getEntityName();
        return name != null ? name : "Unknown";
    }
}
