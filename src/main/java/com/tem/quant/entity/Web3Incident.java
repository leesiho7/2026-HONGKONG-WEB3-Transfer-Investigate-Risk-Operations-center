package com.tem.quant.entity;

import jakarta.persistence.*; // Spring Boot 3 기준 (2라면 javax.persistence)
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
public class Web3Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String caseId;
    private String title;
    private String description;

    // 핵심: DB에 "CRITICAL" 등의 문자열로 저장함
    @Enumerated(EnumType.STRING)
    private Severity severity;

    private String status;
    private String assignee; // 사건 담당자 이름

    @Column(unique = true)
    private String txHash;

    private Double amount;
    private String assetSymbol;
    private String fromAddress;
    private String chainName;
    private LocalDateTime createdAt;

    // 거래소 모니터링 / 리스크 분석 필드
    private String pair;             // ETH/USDT
    private Integer riskScore;       // 0-100
    private String exchangeTag;      // "HashKey Exchange", "OKX Hot Wallet" 등
    private Boolean criticalExchange; // 알려진 거래소 지갑과 연관 여부

    // 인시던트 타입 (UI 분류용)
    private String incidentType;     // "Massive Whale Transfer" | "Whale Dump Detected" | "Liquidity Removal" | "Unusual Exchange Inflow"

    // Template compatibility getters
    public String getAddress() { return fromAddress; }
    public String getChain() { return chainName; }

    /** 인시던트 타입 아이콘 반환 */
    public String getIncidentTypeIcon() {
        if (incidentType == null) return "⚠";
        return switch (incidentType) {
            case "Massive Whale Transfer"  -> "🐋";
            case "Whale Dump Detected"     -> "📉";
            case "Liquidity Removal"       -> "💧";
            case "Unusual Exchange Inflow" -> "🏦";
            default -> "⚠";
        };
    }

    /** CSS 클래스용 slug 반환 */
    public String getIncidentTypeSlug() {
        if (incidentType == null) return "unknown";
        return switch (incidentType) {
            case "Massive Whale Transfer"  -> "whale-transfer";
            case "Whale Dump Detected"     -> "whale-dump";
            case "Liquidity Removal"       -> "liquidity";
            case "Unusual Exchange Inflow" -> "exchange-inflow";
            default -> "unknown";
        };
    }

    /** USD 환산 (ETH 기준, 약식) */
    public String getUsdValue() {
        if (amount == null) return "—";
        double usd = amount * 2_400.0;
        if (usd >= 1_000_000_000) return String.format("$%.1fB", usd / 1_000_000_000);
        if (usd >= 1_000_000)     return String.format("$%.1fM", usd / 1_000_000);
        if (usd >= 1_000)         return String.format("$%.1fK", usd / 1_000);
        return String.format("$%.0f", usd);
    }
    
    public String getTimeAgo() {
        if (createdAt == null) return "just now";
        long minutes = java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        return (minutes / 60) + "h ago";
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.caseId == null) {
            this.caseId = "CASE-" + (System.currentTimeMillis() % 10000);
        }
    }
}