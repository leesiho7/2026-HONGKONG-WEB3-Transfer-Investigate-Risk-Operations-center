package com.tem.quant.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Arkham Intelligence API 클라이언트 (Enterprise Edition)
 *
 * 주요 기능:
 *   - 주소 엔티티 식별: Exchange / OTC / Mixer / Darknet / Sanctioned / DeFi / Bridge
 *   - Caffeine 캐시 10분 TTL — address_labels DB 저장 전 완충 레이어
 *   - 429 Rate Limit 자동 재시도 (최대 2회, 2초 간격)
 *   - 5초 타임아웃으로 파이프라인 블로킹 방지
 *
 * 버그 수정 (이전 버전):
 *   - lombok.Value import → Spring @Value 로 교체
 *   - 생성자에서 baseUrl null 참조 → @PostConstruct 지연 초기화로 교체
 */
@Service
@Slf4j
public class ArkhamApiService {

    @Value("${arkham.api.key}")
    private String apiKey;

    @Value("${arkham.api.base-url}")
    private String baseUrl;

    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    public ArkhamApiService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl.trim())
            .defaultHeader("API-Key", apiKey.trim())
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
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

        /**
         * 엔티티 이름 반환 — entity → label → null 순서
         */
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
            if (arkhamEntity != null && arkhamEntity.getType() != null) {
                return arkhamEntity.getType().toLowerCase();
            }
            if (arkhamLabel != null && arkhamLabel.getType() != null) {
                return arkhamLabel.getType().toLowerCase();
            }
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
     * 캐시 설정: application.properties
     *   spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=600s
     *
     * @param address Ethereum 주소 (대소문자 무관)
     * @return 엔티티 정보, 알 수 없거나 API 실패 시 null
     */
    @Cacheable(value = "arkhamAddress", key = "#address.toLowerCase()", unless = "#result == null")
    public ArkhamAddressResponse getAddressIntel(String address) {
        if (address == null || address.isBlank()) return null;

        String normalizedAddress = address.toLowerCase();
        log.debug("[Arkham] 주소 조회 요청: {}", normalizedAddress.substring(0, Math.min(10, normalizedAddress.length())));

        try {
            return webClient.get()
                .uri("/addresses/{address}", normalizedAddress)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp -> {
                    if (resp.statusCode().value() == 429) {
                        return Mono.error(new ArkhamRateLimitException("rate limited"));
                    }
                    // 404 = 주소 정보 없음 → null 반환
                    return Mono.empty();
                })
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                    Mono.error(new RuntimeException("Arkham server error: " + resp.statusCode())))
                .bodyToMono(ArkhamAddressResponse.class)
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(2))
                    .filter(e -> e instanceof ArkhamRateLimitException))
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(resp -> {
                    if (resp != null && resp.isIdentified()) {
                        log.info("[Arkham] 식별 성공: {} → {} ({})",
                            normalizedAddress.substring(0, Math.min(10, normalizedAddress.length())),
                            resp.getEntityName(), resp.getEntityType());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[Arkham] 조회 실패 {}: {}", normalizedAddress.substring(0, Math.min(10, normalizedAddress.length())), e.getMessage());
                    return Mono.empty();
                })
                .block();
        } catch (Exception e) {
            log.warn("[Arkham] 예외 처리 {}: {}", address, e.getMessage());
            return null;
        }
    }

    /**
     * 기존 호환성 유지 — 엔티티 이름 문자열 반환
     */
    public String getAddressLabel(String address) {
        ArkhamAddressResponse resp = getAddressIntel(address);
        if (resp == null) return "Unknown";
        String name = resp.getEntityName();
        return name != null ? name : "Unknown";
    }

    // ── 내부 예외 ─────────────────────────────────────────────────────────────

    static class ArkhamRateLimitException extends RuntimeException {
        ArkhamRateLimitException(String msg) { super(msg); }
    }
}
