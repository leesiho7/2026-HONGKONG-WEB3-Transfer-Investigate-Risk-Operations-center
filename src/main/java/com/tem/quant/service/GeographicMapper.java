package com.tem.quant.service;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Random;

@Component
public class GeographicMapper {

    private final Random random = new Random();
    private final List<String> ALL_HUBS = List.of("SFO", "NYC", "LON", "DXB", "SIN", "HKG", "SEO", "TYO");

    public String mapToHub(String label) {
        if (label == null || label.isBlank()
                || label.equalsIgnoreCase("Unknown")
                || label.equalsIgnoreCase("Unknown Wallet")) {
            return getRandomHub();
        }

        String l = label.toLowerCase();
        if (l.contains("binance"))                        return "SIN"; // 싱가포르
        if (l.contains("okx") || l.contains("okex"))     return "HKG"; // 홍콩
        if (l.contains("hashkey"))                        return "HKG"; // 홍콩
        if (l.contains("huobi") || l.contains("htx"))    return "HKG"; // 홍콩
        if (l.contains("bybit") || l.contains("kucoin")) return "SIN"; // 싱가포르
        if (l.contains("upbit") || l.contains("bithumb") || l.contains("korbit")) return "SEO";
        if (l.contains("coinbase") || l.contains("kraken")) return "SFO";
        if (l.contains("gemini") || l.contains("cumberland")) return "NYC";
        if (l.contains("lazarus") || l.contains("hack") || l.contains("exploit")) return "NYC";
        if (l.contains("uniswap") || l.contains("defi") || l.contains("aave")) return "NYC";
        if (l.contains("whale"))                          return "NYC";
        if (l.contains("dubai") || l.contains("uae"))    return "DXB";
        if (l.contains("london") || l.contains("ftx"))   return "LON";

        // 알 수 없는 주소는 랜덤 허브 → from/to 허브가 겹쳐서 플로우가 스킵되는 것 방지
        return getRandomHub();
    }

    public String getRandomHub() {
        return ALL_HUBS.get(random.nextInt(ALL_HUBS.size()));
    }
}