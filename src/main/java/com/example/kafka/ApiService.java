package com.example.kafka;

import com.example.kafka.health.ApiHealthIndicator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for making external API calls and tracking their health.
 * Implements retry logic and health monitoring for robust API communication.
 * 
 * Features:
 * 1. Automatic retries for transient failures
 * 2. Health monitoring integration
 * 3. Response time tracking
 * 4. Detailed error logging
 * 
 * The service uses Spring Retry for automatic retries and
 * integrates with ApiHealthIndicator for health monitoring.
 * 
 * Example Usage:
 * <pre>
 * // 1. Basic API call
 * ApiService apiService = new ApiService(restTemplate, healthIndicator);
 * boolean success = apiService.postMessage("{"timestamp": "2025-02-15T19:00:00"}");
 * 
 * // 2. Error handling
 * try {
 *     apiService.postMessage(message);
 * } catch (ApiException e) {
 *     // Handle non-retryable errors
 *     log.error("API call failed permanently: {}", e.getMessage());
 * }
 * 
 * // 3. Health monitoring
 * // Check API health status:
 * GET /actuator/health/api
 * {
 *   "status": "UP",
 *   "details": {
 *     "consecutiveFailures": 0,
 *     "lastSuccessTime": "PT2.345S",
 *     "averageResponseTime": 156.7
 *   }
 * }
 * 
 * // 4. Metrics monitoring
 * // View API metrics:
 * GET /actuator/metrics/api.response.time
 * GET /actuator/metrics/api.health.success.rate
 * </pre>
 */
@Service
@Slf4j
public class ApiService {
    private final RestTemplate restTemplate;
    private final ApiHealthIndicator healthIndicator;
    private static final String API_URL = "https://jsonplaceholder.typicode.com/posts";

    public ApiService(RestTemplate restTemplate, ApiHealthIndicator healthIndicator) {
        this.restTemplate = restTemplate;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Posts a message to the external API with retry capability.
     * 
     * Retry Configuration:
     * - Max 3 attempts
     * - 1 second initial delay
     * - Exponential backoff (multiplier = 2)
     * - Retries on HttpServerErrorException and ResourceAccessException
     * 
     * Health Tracking:
     * - Records response times
     * - Tracks success/failure rates
     * - Updates health status
     * 
     * @param message The message to post
     * @return true if successful, false otherwise
     * @throws ApiException for non-retryable failures
     */
    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean postMessage(String message) {
        long startTime = System.currentTimeMillis();
        try {
            // Make API call with timing
            ResponseEntity<String> response = restTemplate.postForEntity(
                API_URL,
                new PostRequest(message),
                String.class
            );
            
            // Process response and update health metrics
            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                long responseTime = System.currentTimeMillis() - startTime;
                healthIndicator.recordSuccess(responseTime);
                log.info("API call successful, response time: {}ms", responseTime);
                return true;
            } else {
                String error = "API returned unsuccessful status";
                healthIndicator.recordFailure(error);
                log.error(error);
                return false;
            }

        } catch (HttpServerErrorException e) {
            // Retryable server errors (5xx)
            String error = "Server error (will retry): " + e.getMessage();
            healthIndicator.recordFailure(error);
            log.error(error, e);
            throw e;
        } catch (Exception e) {
            // Non-retryable errors (4xx, network issues, etc.)
            String error = "Non-retryable error: " + e.getMessage();
            healthIndicator.recordFailure(error);
            log.error(error, e);
            throw new ApiException("Failed to post message: " + e.getMessage(), e);
        }
    }

    // Simple request body class
    private static class PostRequest {
        private final String title;
        private final String body;
        private final int userId = 1;

        public PostRequest(String message) {
            this.title = "Kafka Message";
            this.body = message;
        }

        public String getTitle() { return title; }
        public String getBody() { return body; }
        public int getUserId() { return userId; }
    }
}