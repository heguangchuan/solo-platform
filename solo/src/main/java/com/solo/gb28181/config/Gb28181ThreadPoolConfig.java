package com.solo.gb28181.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    // 目录批量入库单独跑，别把 SIP 消息线程拖住；池满由调用线程执行，顺便回压
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[GB28181] 通道目录同步线程池初始化完成 core={} max={} queue={}",
                cfg.getCorePoolSize(), cfg.getMaxPoolSize(), cfg.getQueueCapacity());
        return executor;
    }
}
