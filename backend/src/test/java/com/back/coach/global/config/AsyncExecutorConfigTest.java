package com.back.coach.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExecutorConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncExecutorConfig.class);

    @Test
    void defaultProperties_registerExecutorBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AsyncExecutorProperties.class);
            assertThat(context).hasBean(AsyncExecutorConfig.ANALYSIS_TASK_EXECUTOR);
            assertThat(context).hasBean(AsyncExecutorConfig.PARALLEL_ANALYSIS_EXECUTOR);

            ThreadPoolTaskExecutor analysis =
                    context.getBean(AsyncExecutorConfig.ANALYSIS_TASK_EXECUTOR, ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor parallelAnalysis =
                    context.getBean(AsyncExecutorConfig.PARALLEL_ANALYSIS_EXECUTOR, ThreadPoolTaskExecutor.class);

            assertExecutor(analysis, 2, 4, 100, "analysis-task-");
            assertExecutor(parallelAnalysis, 2, 2, 20, "parallel-analysis-");
        });
    }

    @Test
    void propertyOverride_appliesToExecutorBeans() {
        contextRunner
                .withPropertyValues(
                        "coach.async.analysis.core-pool-size=3",
                        "coach.async.analysis.max-pool-size=5",
                        "coach.async.analysis.queue-capacity=50",
                        "coach.async.analysis.thread-name-prefix=custom-analysis-",
                        "coach.async.parallel-analysis.core-pool-size=1",
                        "coach.async.parallel-analysis.max-pool-size=3",
                        "coach.async.parallel-analysis.queue-capacity=10",
                        "coach.async.parallel-analysis.thread-name-prefix=custom-parallel-"
                )
                .run(context -> {
                    ThreadPoolTaskExecutor analysis =
                            context.getBean(AsyncExecutorConfig.ANALYSIS_TASK_EXECUTOR, ThreadPoolTaskExecutor.class);
                    ThreadPoolTaskExecutor parallelAnalysis =
                            context.getBean(AsyncExecutorConfig.PARALLEL_ANALYSIS_EXECUTOR, ThreadPoolTaskExecutor.class);

                    assertExecutor(analysis, 3, 5, 50, "custom-analysis-");
                    assertExecutor(parallelAnalysis, 1, 3, 10, "custom-parallel-");
                });
    }

    @Test
    void invalidPoolSize_failsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "coach.async.analysis.core-pool-size=5",
                        "coach.async.analysis.max-pool-size=4"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("coach.async.analysis.max-pool-size must be greater than or equal to core-pool-size"));
    }

    private void assertExecutor(
            ThreadPoolTaskExecutor executor,
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        assertThat(executor.getCorePoolSize()).isEqualTo(corePoolSize);
        assertThat(executor.getMaxPoolSize()).isEqualTo(maxPoolSize);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(queueCapacity);
        assertThat(executor.getThreadNamePrefix()).isEqualTo(threadNamePrefix);
    }
}
