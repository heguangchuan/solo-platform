package com.solo.gb28181.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
public class Gb28181ThreadPoolConfig {

    private static final AtomicInteger BIZ_POOL_THREAD_NUMBER = new AtomicInteger(1);
    private static final AtomicInteger CATALOG_POOL_THREAD_NUMBER = new AtomicInteger(1);

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
            Thread thread = new Thread(r, "gb28181-biz-" + BIZ_POOL_THREAD_NUMBER.getAndIncrement());
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

    /**
     * 通道目录同步专用线程池。
     * <p>与 {@link #gb28181BizExecutor} 隔离：Catalog 落库是 10w+ 通道场景下的 IO 密集型重活，
     * 跑在业务池上会拖垮 SIP 消息分发。这里专门起一个独立池做分片入库、批量 upsert。</p>
     * <ul>
     *   <li>core 20 / max 50：DB 写并发能撑住且不抢占 DB 连接池</li>
     *   <li>queue 5000：长任务排队</li>
     *   <li>CallerRunsPolicy：撑满就回压到调用者（CatalogDataCache 落盘线程），
     *       让上游慢下来而不是丢任务</li>
     * </ul>
     */
    @Bean("catalogSyncExecutor")
    public ThreadPoolTaskExecutor catalogSyncExecutor(Gb28181Properties properties) {
        Gb28181Properties.CatalogSyncPool cfg = properties.getCatalogSyncPool();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("gb-catalog-sync-");
        executor.setDaemon(true);
        // setRejectedExecutionHandler 走 setter 即可，Spring 会包装成 RejectedExecutionHandler
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 不等待任务在关停时跑完——短任务在 shutdown 之前大多已落库
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[GB28181] 通道目录同步线程池初始化完成 core={} max={} queue={}",
                cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity());
        return executor;
    }
}
