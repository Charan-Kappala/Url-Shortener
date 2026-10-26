package com.urlshortener.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter cacheHitCounter(MeterRegistry registry) {
        return Counter.builder("url.cache.hit")
                .description("Number of Redis cache hits")
                .register(registry);
    }

    @Bean
    public Counter cacheMissCounter(MeterRegistry registry) {
        return Counter.builder("url.cache.miss")
                .description("Number of Redis cache misses")
                .register(registry);
    }
}
