package com.example.kafka.health;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Custom health indicator for monitoring API health status.
 * Integrates with Spring Boot Actuator to provide detailed health information
 * about the external API that our Kafka consumer interacts with.
 * 
 * Features:
 * 1. Tracks consecutive failures (marks service as DOWN after threshold)
 * 2. Records response times with percentiles
 * 3. Maintains last error message
 * 4. Tracks time since last successful call
 * 
 * Example Usage:
 * <pre>
 * // 1. Basic Health Check
 * // Access the health endpoint:
 * GET /actuator/health/api
 * {
 *   "status": "UP",
 *   "details": {
 *     "consecutiveFailures": 0,
 *     "lastSuccessTime": "PT2.345S",
 *     "averageResponseTime": 156.7,
 *     "p95ResponseTime": 234.5
 *   }
 * }
 * 
 * // 2. Failure Detection
 * // After 3 consecutive failures:
 * GET /actuator/health/api
 * {
 *   "status": "DOWN",
 *   "details": {
 *     "consecutiveFailures": 3,
 *     "lastError": "API returned unsuccessful status",
 *     "timeSinceLastSuccess": "PT5M"
 *   }
 * }
 * 
 * // 3. Custom Integration Example
 * @Service
 * public class CustomService {
 *     private final ApiHealthIndicator healthIndicator;
 *     
 *     public void performOperation() {
 *         try {
 *             long startTime = System.currentTimeMillis();
 *             // ... perform operation ...
 *             healthIndicator.recordSuccess(
 *                 System.currentTimeMillis() - startTime
 *             );
 *         } catch (Exception e) {
 *             healthIndicator.recordFailure(e.getMessage());
 *             throw e;
 *         }
 *     }
 * }
 * 
 * // 4. Monitoring API Response Times
 * // View response time metrics:
 * GET /actuator/metrics/api.response.time
 * {
 *   "name": "api.response.time",
 *   "measurements": [
 *     {"statistic": "COUNT", "value": 1234},
 *     {"statistic": "TOTAL_TIME", "value": 123.45},
 *     {"statistic": "MAX", "value": 0.234},
 *     {"statistic": "P95", "value": 0.156}
 *   ]
 * }
 * 
 * // 5. Alert Configuration Example
 * // Using Prometheus Alert Rules:
 * groups:
 * - name: api-health
 *   rules:
 *   - alert: ApiHealthDown
 *     expr: api_health_status{status="DOWN"} > 0
 *     for: 5m
 *     labels:
 *       severity: critical
 *     annotations:
 *       summary: API Health Check Failed
 * </pre>
 * 
 * This health information is exposed at:
 * - /actuator/health (overall status)
 * - /actuator/health/api (detailed API health)
 */
@Component
public class ApiHealthIndicator implements HealthIndicator {
    // Atomic variables for thread-safe tracking of health metrics
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private final AtomicReference<Long> lastSuccessTime = new AtomicReference<>(System.currentTimeMillis());
    
    // Number of consecutive failures before marking service as DOWN
    private static final int FAILURE_THRESHOLD = 3;
    
    // Timer for tracking API response times
    private final Timer apiResponseTimer;
    
    public ApiHealthIndicator(MeterRegistry registry) {
        // Configure response time timer with percentiles
        this.apiResponseTimer = Timer.builder("api.response.time")
            .description("API response time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
    
    /**
     * Implements the health check logic.
     * Called by Spring Boot Actuator when /actuator/health is accessed.
     * 
     * Health Status Rules:
     * - DOWN if consecutive failures >= threshold
     * - UP otherwise
     * 
     * @return Health object with status and detailed metrics
     */
    @Override
    public Health health() {
        int failures = consecutiveFailures.get();
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime.get();
        
        if (failures >= FAILURE_THRESHOLD) {
            // Service is considered DOWN
            return Health.down()
                .withDetail("consecutiveFailures", failures)
                .withDetail("lastError", lastError.get())
                .withDetail("timeSinceLastSuccess", Duration.ofMillis(timeSinceLastSuccess).toString())
                .build();
        }
        
        // Service is UP - include performance metrics
        return Health.up()
            .withDetail("consecutiveFailures", failures)
            .withDetail("lastSuccessTime", Duration.ofMillis(timeSinceLastSuccess).toString())
            //.withDetail("averageResponseTime", apiResponseTimer.mean())
            //.withDetail("p95ResponseTime", apiResponseTimer.percentile(0.95))
            .build();
    }
    
    /**
     * Records a successful API call.
     * - Resets failure counter
     * - Updates last success timestamp
     * - Records response time for percentile calculations
     * 
     * @param responseTimeMs Time taken for the API call in milliseconds
     */
    public void recordSuccess(long responseTimeMs) {
        consecutiveFailures.set(0);
        lastSuccessTime.set(System.currentTimeMillis());
        apiResponseTimer.record(Duration.ofMillis(responseTimeMs));
    }
    
    /**
     * Records a failed API call.
     * - Increments consecutive failure counter
     * - Updates last error message
     * - May trigger health status change to DOWN if threshold is reached
     * 
     * @param error Description of what went wrong
     */
    public void recordFailure(String error) {
        consecutiveFailures.incrementAndGet();
        lastError.set(error);
    }
}
