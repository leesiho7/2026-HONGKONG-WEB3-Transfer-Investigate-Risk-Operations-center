package com.tem.quant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * QuickNode Stream / OKLink API 에서 수집한 고래 트랜잭션 엔티티
 * 1000 ETH 이상 또는 100 BTC 이상의 대형 이체만 저장됩니다.
 */
@Entity
@Getter @Setter
@NoArgsConstructor
public class WhaleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String txHash;

    private String chainName;       // "Ethereum" | "Bitcoin"
    private String assetSymbol;     // "ETH" | "BTC"
    private Double amount;          // ETH 또는 BTC 단위
    private Double amountUsd;       // 환산 USD 가치

    private String fromAddress;
    private String toAddress;

    // OKLink 라벨링 결과
    private String fromLabel;       // e.g. "Binance Hot Wallet"
    private String toLabel;         // e.g. "Unknown"
    private String fromLabelType;   // "Exchange" | "Hacker" | "ColdWallet" | "Whale" | "Unknown"
    private String toLabelType;

    // 위험 분류
    private String riskLevel;       // "CRITICAL" | "HIGH" | "MEDIUM" | "INFO"

    // Arkham Intelligence 분류 (Enterprise)
    private String transactionCategory; // EXCHANGE_INFLOW | OTC_TRADE | MIXER_DEPOSIT | ...
    private String arkhamEntityFrom;    // Arkham 식별 발신 엔티티명
    private String arkhamEntityTo;      // Arkham 식별 수신 엔티티명

    private Long blockNumber;
    private LocalDateTime detectedAt;

    @PrePersist
    public void prePersist() {
        this.detectedAt = LocalDateTime.now();
    }

    /** 사람이 읽기 쉬운 시간 표기 */
    public String getTimeAgo() {
        if (detectedAt == null) return "just now";
        long seconds = java.time.Duration.between(detectedAt, LocalDateTime.now()).toSeconds();
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return (seconds / 60) + "m ago";
        return (seconds / 3600) + "h ago";
    }

    /** ETH/BTC 단위 포맷 */
    public String getFormattedAmount() {
        if (amount == null) return "0";
        return String.format("%,.1f %s", amount, assetSymbol != null ? assetSymbol : "");
    }
}
