package com.example.kafka.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import java.time.Duration;
import java.util.Map;

/**
 * Centralized metrics configuration for Kafka consumer application.
 * This class provides a clean separation between business logic and metrics collection.
 * 
 * Key features:
 * 1. Message processing metrics (success, failures, DLT)
 * 2. Timing metrics with percentiles
 * 3. API health monitoring
 * 4. Success rate calculation
 * 
 * Example Usage:
 * <pre>
 * // 1. View message processing metrics
 * GET /actuator/metrics/kafka.consumer.messages.processed
 * {
 *   "name": "kafka.consumer.messages.processed",
 *   "measurements": [{"statistic": "COUNT", "value": 1234}],
 *   "availableTags": [{"tag": "type", "values": ["success"]}]
 * }
 * 
 * // 2. Monitor processing times with percentiles
 * GET /actuator/metrics/kafka.consumer.message.processing
 * {
 *   "name": "kafka.consumer.message.processing",
 *   "measurements": [
 *     {"statistic": "COUNT", "value": 1234},
 *     {"statistic": "TOTAL_TIME", "value": 123.45},
 *     {"statistic": "MAX", "value": 0.234},
 *     {"statistic": "P95", "value": 0.156}
 *   ]
 * }
 * 
 * // 3. Track API health
 * GET /actuator/metrics/api.health.success.rate
 * {
 *   "name": "api.health.success.rate",
 *   "measurements": [{"statistic": "VALUE", "value": 0.987}]
 * }
 * 
 * // 4. Adding custom metrics in your code:
 * // Counter example
 * Counter.builder("custom.events")
 *        .tag("type", "special")
 *        .description("Count of special events")
 *        .register(registry)
 *        .increment();
 * 
 * // Timer example
 * Timer.builder("custom.operation")
 *      .tag("type", "background")
 *      .publishPercentiles(0.5, 0.95)
 *      .register(registry);
 * 
 * // Gauge example
 * Queue<Request> queue = new LinkedBlockingQueue<>();
 * Gauge.builder("queue.size", queue, Queue::size)
 *      .register(registry);
 * </pre>
 * 
 * All metrics are automatically registered with Micrometer and exposed via 
 * Spring Boot Actuator endpoints (/actuator/metrics, /actuator/prometheus)
 */
@Configuration
@Getter
public class KafkaMetrics {

    @Value("${spring.application.name:kafka-consumer}")
    private String applicationName;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Creates CloudWatch meter registry for AWS integration.
     */
    @Bean
    @Profile("prod")
    public CloudWatchMeterRegistry cloudWatchMeterRegistry() {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String namespace() {
                return applicationName;
            }

            @Override
            public Duration step() {
                return Duration.ofMinutes(1);
            }
        };

        CloudWatchAsyncClient cloudWatchAsyncClient = CloudWatchAsyncClient.builder()
            .region(Region.of(awsRegion))
            .build();

        return new CloudWatchMeterRegistry(config, Clock.SYSTEM, cloudWatchAsyncClient);
    }

    private final MeterRegistry registry;

    // Counter metrics for tracking various message states
    private final Counter processedMessagesCounter;
    private final Counter failedMessagesCounter;
    private final Counter dltMessagesCounter;
    private final Counter apiFailuresCounter;

    // Timer metrics for measuring duration of operations
    private final Timer batchProcessingTimer;
    private final Timer messageProcessingTimer;
    private final Timer apiCallTimer;
    private final Timer dltProcessingTimer;

    public KafkaMetrics(MeterRegistry registry) {
        this.registry = registry;

        // === Message Processing Counters ===
        
        // Tracks successfully processed messages
        this.processedMessagesCounter = Counter.builder("kafka.consumer.messages.processed")
            .description("Number of successfully processed messages")
            .tag("type", "success")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .register(registry);

        // Tracks failed message processing attempts
        this.failedMessagesCounter = Counter.builder("kafka.consumer.messages.failed")
            .description("Number of failed messages")
            .tag("type", "failure")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .register(registry);

        // Tracks messages sent to Dead Letter Topic
        this.dltMessagesCounter = Counter.builder("kafka.consumer.messages.dlt")
            .description("Number of messages sent to DLT")
            .tag("type", "dlt")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .register(registry);

        // Tracks API-specific failures
        this.apiFailuresCounter = Counter.builder("kafka.consumer.api.failures")
            .description("Number of API call failures")
            .tag("type", "api_failure")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .register(registry);

        // === Timer Metrics ===
        // All timers include 50th, 95th, and 99th percentiles for better insight
        
        // Measures time taken to process entire batch of messages
        this.batchProcessingTimer = Timer.builder("kafka.consumer.batch.processing")
            .description("Time taken to process message batches")
            .tag("operation", "batch_processing")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .publishPercentiles(0.5, 0.95, 0.99) // Tracks p50, p95, p99
            .register(registry);

        // Measures time taken to process individual messages
        this.messageProcessingTimer = Timer.builder("kafka.consumer.message.processing")
            .description("Time taken to process individual messages")
            .tag("operation", "message_processing")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        // Measures API call duration
        this.apiCallTimer = Timer.builder("kafka.consumer.api.call")
            .description("Time taken for API calls")
            .tag("operation", "api_call")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        // Measures time spent processing DLT messages
        this.dltProcessingTimer = Timer.builder("kafka.consumer.dlt.processing")
            .description("Time taken to process DLT messages")
            .tag("operation", "dlt_processing")
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        // === API Health Gauges ===
        // These metrics provide real-time insight into API health
        
        // Tracks the current streak of consecutive API failures
        // Available at: /actuator/metrics/api.health.consecutive.failures
        Gauge.builder("api.health.consecutive.failures", 
                     () -> registry.find("api.response.time").timer().count())
            .description("Number of consecutive API failures")
            .register(registry);

        // Calculates and exposes the current API success rate (0.0 to 1.0)
        // Available at: /actuator/metrics/api.health.success.rate
        Gauge.builder("api.health.success.rate",
                     () -> {
                         Timer apiTimer = registry.find("api.response.time").timer();
                         long total = apiTimer.count();
                         long failures = registry.find("kafka.consumer.api.failures")
                                               .counter()
                                               .count();
                         return total == 0 ? 1.0 : (total - failures) / (double) total;
                     })
            .description("API call success rate")
            .register(registry);

        // Add AWS-specific gauges
        Gauge.builder("kafka.consumer.memory.used",
            Runtime.getRuntime(),
            this::getUsedMemoryMB)
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .description("JVM memory used in MB")
            .register(registry);

        Gauge.builder("kafka.consumer.memory.max",
            Runtime.getRuntime(),
            this::getMaxMemoryMB)
            .tag("application", applicationName)
            .tag("region", awsRegion)
            .description("JVM max memory in MB")
            .register(registry);
    }

    private double getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
    }

    private double getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0);
    }

    /**
     * Increment methods for updating counters.
     * These are called by MetricsAspect to track various events.
     */
    public void incrementProcessedMessages() {
        processedMessagesCounter.increment();
    }

    public void incrementFailedMessages() {
        failedMessagesCounter.increment();
    }

    public void incrementDltMessages() {
        dltMessagesCounter.increment();
    }

    public void incrementApiFailures() {
        apiFailuresCounter.increment();
    }
}
