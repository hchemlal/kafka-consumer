package com.example.kafka;

import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Service
public class ApiService {
    private final RestTemplate restTemplate;
    private static final String API_URL = "https://jsonplaceholder.typicode.com/posts";

    public ApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Retryable(
        value = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean postMessage(String message) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                API_URL,
                new PostRequest(message),
                String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpServerErrorException e) {
            // Retryable - will be caught and retried by @Retryable
            throw e;
        } catch (Exception e) {
            // Non-retryable
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