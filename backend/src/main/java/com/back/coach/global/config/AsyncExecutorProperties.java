package com.back.coach.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coach.async")
public record AsyncExecutorProperties(
        PoolProperties analysis,
        PoolProperties parallelAnalysis
) {

    private static final PoolProperties DEFAULT_ANALYSIS =
            new PoolProperties("coach.async.analysis", 2, 4, 100, "analysis-task-");
    private static final PoolProperties DEFAULT_PARALLEL_ANALYSIS =
            new PoolProperties("coach.async.parallel-analysis", 2, 2, 20, "parallel-analysis-");

    public AsyncExecutorProperties {
        analysis = analysis == null
                ? DEFAULT_ANALYSIS
                : analysis.withDefaults("coach.async.analysis", DEFAULT_ANALYSIS);
        parallelAnalysis = parallelAnalysis == null
                ? DEFAULT_PARALLEL_ANALYSIS
                : parallelAnalysis.withDefaults("coach.async.parallel-analysis", DEFAULT_PARALLEL_ANALYSIS);
    }

    public record PoolProperties(
            String name,
            Integer corePoolSize,
            Integer maxPoolSize,
            Integer queueCapacity,
            String threadNamePrefix
    ) {

        PoolProperties withDefaults(String propertyName, PoolProperties defaults) {
            PoolProperties resolved = new PoolProperties(
                    propertyName,
                    corePoolSize == null ? defaults.corePoolSize : corePoolSize,
                    maxPoolSize == null ? defaults.maxPoolSize : maxPoolSize,
                    queueCapacity == null ? defaults.queueCapacity : queueCapacity,
                    threadNamePrefix == null ? defaults.threadNamePrefix : threadNamePrefix
            );
            resolved.validate();
            return resolved;
        }

        private void validate() {
            validatePositive(name, "core-pool-size", corePoolSize);
            validatePositive(name, "max-pool-size", maxPoolSize);
            validatePositive(name, "queue-capacity", queueCapacity);
            if (maxPoolSize < corePoolSize) {
                throw new IllegalArgumentException(name + ".max-pool-size must be greater than or equal to core-pool-size");
            }
            if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
                throw new IllegalArgumentException(name + ".thread-name-prefix must not be blank");
            }
        }

        private static void validatePositive(String name, String field, Integer value) {
            if (value == null || value < 1) {
                throw new IllegalArgumentException(name + "." + field + " must be greater than or equal to 1");
            }
        }
    }
}
