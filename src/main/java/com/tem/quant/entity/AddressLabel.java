package com.tem.quant.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "address_labels", indexes = @Index(columnList = "address"))
@Getter @Setter
@NoArgsConstructor
public class AddressLabel {

    @Id
    @Column(length = 66)
    private String address; // 0x... 형식의 주소

    @Column(nullable = false)
    private String groupName; // 예: "Binance", "Upbit", "Lazarus Group"

    @Column(nullable = false)
    private String category; // 예: "EXCHANGE", "HACKER", "WHALE", "DEX"

    private String subTag;   // 예: "Hot Wallet", "Deposit Wallet", "Phishing"

    private String source;   // 데이터 출처 (예: "OKLink", "Etherscan", "Manual")

    private LocalDateTime createdAt = LocalDateTime.now();
}