package com.tem.quant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "whale_logs")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhaleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String txHash;

    private String fromAddress;
    private String toAddress;
    
    // 라벨링 정보 (조회 성능을 위해 비정규화 저장)
    private String fromGroupName; 
    private String toGroupName;

    private BigDecimal amount;
    private String symbol; // ETH, USDT 등

    private LocalDateTime blockTime;
}