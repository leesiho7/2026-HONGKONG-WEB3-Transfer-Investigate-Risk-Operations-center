package com.tem.quant.service;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Random;

@Component
public class GeographicMapper {

    private final Random random = new Random();
    private final List<String> ALL_HUBS = List.of("SFO", "NYC", "LON", "DXB", "SIN", "HKG", "SEO", "TYO");

    public String mapToHub(String label) {
        if (label == null || label.equals("Unknown Wallet")) {
            return getRandomHub();
        }

        String l = label.toLowerCase();
        if (l.contains("binance") || l.contains("okx")) return "SIN"; // 싱가포르
        if (l.contains("upbit") || l.contains("bithumb")) return "SEO"; // 서울
        if (l.contains("coinbase") || l.contains("kraken")) return "SFO"; // 샌프란시스코
        if (l.contains("whale")) return "NYC"; // 뉴욕 (거물은 뉴욕으로!)
        
        return "HKG"; // 기본값은 홍콩 Web3 Festival!
    }

    public String getRandomHub() {
        return ALL_HUBS.get(random.nextInt(ALL_HUBS.size()));
    }
}