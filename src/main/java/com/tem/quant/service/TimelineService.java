package com.tem.quant.service;

import com.tem.quant.entity.InvestigationTimeline;
import com.tem.quant.entity.Web3Incident;
import com.tem.quant.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TimelineService {

    private final TimelineRepository timelineRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 타임라인 로그를 기록하고, 해당 케이스를 보고 있는 모든 운영자에게 실시간 전송합니다.
     */
    @Transactional
    public void logAction(Web3Incident incident, String operator, String action, String comment) {
        InvestigationTimeline timeline = new InvestigationTimeline();
        timeline.setIncident(incident);
        timeline.setOperatorName(operator);
        timeline.setAction(action);
        timeline.setComment(comment);
        timeline.setTimestamp(LocalDateTime.now());

        InvestigationTimeline saved = timelineRepository.save(timeline);

        // 해당 케이스의 상세 페이지를 보고 있는 모든 운영자에게 즉시 전송
        Map<String, Object> payload = Map.of(
            "id",           saved.getId(),
            "operatorName", operator,
            "action",       action,
            "comment",      comment != null ? comment : "",
            "timestamp",    saved.getTimestamp().toString()
        );
        messagingTemplate.convertAndSend("/topic/timeline/" + incident.getCaseId(), payload);
    }
}