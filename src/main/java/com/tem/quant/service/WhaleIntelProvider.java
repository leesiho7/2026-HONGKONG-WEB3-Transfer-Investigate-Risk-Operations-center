package com.tem.quant.service;

import com.tem.quant.entity.WhaleTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WhaleIntelProvider {

    // 주소 라벨링 (DB 조회 로직을 여기에 연결하세요)
    public String getLabelForAddress(String address) {
        // TODO: repository.findLabelByAddress(address) 호출
        // 임시 테스트용 로직
        if (address.equalsIgnoreCase("0x28C6c06298d514Db089934071355E5743bf21d60")) return "Binance-Hot-Wallet";
        if (address.equalsIgnoreCase("0x742d35Cc6634C0532925a3b844Bc454e4438f44e")) return "Whale-Wallet-Alpha";
        return "Unknown Wallet";
    }

    // 최종 리스크 점수 계산 (0~100)
    public int calculateRiskScore(double amount, String fromLabel, String toLabel) {
        int score = 40; // 기본 점수

        // 1. 금액 가중치
        if (amount >= 5000) score += 30;
        else if (amount >= 2000) score += 15;

        // 2. 거래소 유입/유출 가중치
        if (toLabel.contains("Exchange") || toLabel.contains("Wallet")) score += 20;
        
        return Math.min(score, 100);
    }
}