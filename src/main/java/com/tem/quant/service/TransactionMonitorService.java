package com.tem.quant.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Convert;

import com.tem.quant.entity.AddressLabel;
import com.tem.quant.entity.WhaleLog;
import com.tem.quant.repository.AddressLabelRepository;
import com.tem.quant.repository.WhaleLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionMonitorService {

    private final AddressLabelRepository addressLabelRepository;
    private final WhaleLogRepository whaleLogRepository;
    private final Web3j web3j;

    // 고래 기준 설정 (예: 10 ETH 이상 거래만 감지)
    private static final BigDecimal WHALE_THRESHOLD = new BigDecimal("10");

    @EventListener(ApplicationReadyEvent.class)
    public void startMonitoring() {
        log.info("🚀 실시간 고래 탐지 및 라벨링 모니터링 시스템 가동...");
        try {
            // HTTP 프로바이더(Cloudflare fallback)는 transactionFlowable() 미지원 → 서버 크래시 방지
            web3j.transactionFlowable().subscribe(tx -> {
                if (tx.getValue() != null) {
                    BigDecimal amount = Convert.fromWei(tx.getValue().toString(), Convert.Unit.ETHER);
                    if (amount.compareTo(WHALE_THRESHOLD) >= 0) {
                        processWhaleTransaction(tx, amount);
                    }
                }
            }, error -> log.error("❌ 모니터링 중 에러 발생: ", error));
        } catch (Exception e) {
            log.warn("⚠️ Web3j 스트리밍 구독 불가 (HTTP 프로바이더는 미지원). " +
                     "QuickNode WebSocket 엔드포인트를 설정하면 실시간 모니터링이 활성화됩니다. 원인: {}", e.getMessage());
        }
    }

    private void processWhaleTransaction(Transaction tx, BigDecimal amount) {
        // 주소 소문자 변환 및 Null 체크 (안전한 데이터 처리)
        String from = tx.getFrom() != null ? tx.getFrom().toLowerCase() : "unknown";
        String to   = tx.getTo()   != null ? tx.getTo().toLowerCase()   : "contract_creation";

        // 2. 직접 구축한 라벨링 DB에서 거래소/그룹 정보 조회
        AddressLabel fromInfo = addressLabelRepository.findById(from).orElse(null);
        AddressLabel toInfo = addressLabelRepository.findById(to).orElse(null);

        String fromLabel = (fromInfo != null) ? fromInfo.getGroupName() : "Unknown";
        String toLabel = (toInfo != null) ? toInfo.getGroupName() : "Unknown";

        // 3. 에러 방지용 안전한 주소 출력 (StringIndexOutOfBoundsException 방지)
        String displayFrom = getSafeAddress(from);
        String displayTo = getSafeAddress(to);

        log.info("🐋 [Whale Alert] {} ETH 이동 감지!", amount);
        log.info("출처: {} ({}) -> 목적지: {} ({})", displayFrom, fromLabel, displayTo, toLabel);

        // 4. WhaleLog 엔티티 생성 및 DB 저장
        try {
            WhaleLog whaleLog = WhaleLog.builder()
                    .txHash(tx.getHash())
                    .fromAddress(from)
                    .toAddress(to)
                    .fromGroupName(fromLabel)
                    .toGroupName(toLabel)
                    .amount(amount)
                    .symbol("ETH")
                    .blockTime(LocalDateTime.now())
                    .build();

            whaleLogRepository.save(whaleLog);
        } catch (Exception e) {
            log.error("❌ 거래 로그 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 주소 문자열을 안전하게 자르는 헬퍼 메서드
     * 에러 로그에 나왔던 StringIndexOutOfBoundsException을 원천 차단합니다.
     */
    private String getSafeAddress(String address) {
        if (address == null) return "Unknown";
        // 주소가 10자보다 짧으면 그대로 반환, 길면 앞 10자만 표시 (로그 가독성용)
        return address.length() > 10 ? address.substring(0, 10) + "..." : address;
    }
}