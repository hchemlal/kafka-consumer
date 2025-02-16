package com.example.kafka.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import java.time.Duration;
import java.util.Map;

@Configuration
@Profile("prod")
public class CloudWatchConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${aws.region}")
    private String region;

    @Value("${management.metrics.export.cloudwatch.namespace}")
    private String namespace;

    @Value("${management.metrics.export.cloudwatch.step}")
    private Duration step;

    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder()
            .region(Region.of(region))
            .credentialsProvider(InstanceProfileCredentialsProvider.create())
            .build();
    }

    @Bean
    public MeterRegistry cloudWatchMeterRegistry(CloudWatchAsyncClient client) {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                Map<String, String> configurations = Map.of(
                    "cloudwatch.namespace", namespace,
                    "cloudwatch.step", step.toString()
                );
                return configurations.get(key);
            }
        };

        return new CloudWatchMeterRegistry(
            config,
            Clock.SYSTEM,
            client
        );
    }
}