package com.solo.gb28181.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GB28181 业务线程池配置
 * <p>
 * 专门的线程池处理 SIP 消息、心跳、点播回调等业务，避免占用 Spring MVC 默认线程池，
 * 保证高并发下发国标业务不会阻塞主业务请求。
 * </p>
 * <ul>
 *   <li>corePoolSize = max(2 * CPU cores, 16)</li>
 *   <li>maximumPoolSize = 10 * corePoolSize</li>
 *   <li>queueCapacity = 10000</li>
 *   <li>拒绝策略: CallerRunsPolicy - 撑满了直接调用者线程跑，不丢任务</li>
 * </ul>
 * </p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "gb28181", name = "enabled", havingValue = "true")
public class Gb28181ThreadPoolConfig {

    private static final AtomicInteger POOL_THREAD_NUMBER = new AtomicInteger(1);

    @Bean("gb28181BizExecutor")
    public ThreadPoolExecutor gb28181BizExecutor(Gb28181Properties properties) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int defaultCore = Math.max(2 * cpuCores, 16);
        int core = properties.getCorePoolSize() != null ? properties.getCorePoolSize() : defaultCore;
        int defaultMax = 10 * core;
        int max = properties.getMaxPoolSize() != null ? properties.getMaxPoolSize() : defaultMax;
        int defaultQueue = 10_000;
        int queue = properties.getQueueCapacity() != null ? properties.getQueueCapacity() : defaultQueue;

        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "gb28181-biz-" + POOL_THREAD_NUMBER.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                core,
                max,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("[GB28181] 业务线程池初始化完成 core={} max={} queue={}", core, max, queue);
        return executor;
    }
}
