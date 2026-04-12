package com.tem.quant.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exchange_location")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeLocation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String exchangeName;   // 예: "Binance", "Upbit", "OKX"

    private Double latitude;       // 위도  (예: 1.3521)
    private Double longitude;      // 경도  (예: 103.8198)

    private String countryCode;    // ISO 국가코드 (예: "SG", "KR", "US")
    private String city;           // 도시명 (선택)
}