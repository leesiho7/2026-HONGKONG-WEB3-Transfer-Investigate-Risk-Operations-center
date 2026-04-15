package com.tem.quant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 웹훅 비동기 처리용 스레드풀 설정
 *
 * CallerRunsPolicy: 풀+큐가 꽉 찼을 때 webhook 수신 스레드가 직접 실행
 *  → QuickNode 응답을 늦춰 자연스러운 백프레셔 형성, OOM 방지
 */
@Configuration
public class AsyncConfig {

    @Value("${whale.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${whale.executor.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${whale.executor.queue-capacity:200}")
    private int queueCapacity;

    @Bean(name = "whaleExecutor")
    public ThreadPoolTaskExecutor whaleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("whale-worker-");
        // 큐까지 꽉 찼을 때: 호출 스레드(Redis consumer)가 직접 처리 → 자동 속도 조절
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
