package com.back.coach.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncExecutorProperties.class)
public class AsyncExecutorConfig {

    public static final String ANALYSIS_TASK_EXECUTOR = "analysisTaskExecutor";
    public static final String PARALLEL_ANALYSIS_EXECUTOR = "parallelAnalysisExecutor";

    @Bean(name = ANALYSIS_TASK_EXECUTOR)
    ThreadPoolTaskExecutor analysisTaskExecutor(AsyncExecutorProperties properties) {
        return executor(properties.analysis());
    }

    @Bean(name = PARALLEL_ANALYSIS_EXECUTOR)
    ThreadPoolTaskExecutor parallelAnalysisExecutor(AsyncExecutorProperties properties) {
        return executor(properties.parallelAnalysis());
    }

    private ThreadPoolTaskExecutor executor(AsyncExecutorProperties.PoolProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
