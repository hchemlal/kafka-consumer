package com.example.kafka.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.example.kafka.ActionEvent;
import com.example.kafka.ApiException;
import java.util.List;

/**
 * Aspect for collecting metrics without cluttering business logic.
 * Uses Spring AOP to intercept method calls and record metrics.
 * 
 * Benefits of using AOP for metrics:
 * 1. Separation of concerns - business logic remains clean
 * 2. Centralized metrics collection
 * 3. Consistent measurement approach
 * 4. Easy to add/modify metrics without changing business code
 * 
 * Example Usage:
 * <pre>
 * // 1. Enable AOP in your application
 * // In Application.java:
 * @EnableAspectJAutoProxy
 * public class Application {
 *     // ...
 * }
 * 
 * // 2. Adding New Metrics
 * // Example: Track message size
 * @Around("execution(* com.example.kafka.KafkaMessageConsumer.processMessage(..))")
 * public Object trackMessageSize(ProceedingJoinPoint joinPoint) {
 *     ConsumerRecord<String, ActionEvent> record = 
 *         (ConsumerRecord<String, ActionEvent>) joinPoint.getArgs()[0];
 *     
 *     // Record message size
 *     Gauge.builder("kafka.message.size", 
 *                   () -> record.value().toString().length())
 *          .tag("topic", record.topic())
 *          .register(registry);
 *     
 *     return joinPoint.proceed();
 * }
 * 
 * // 3. Track Custom Operation Timing
 * @Around("execution(* com.example.service.*.process*(..))")
 * public Object trackCustomOperation(ProceedingJoinPoint joinPoint) {
 *     Timer.Sample sample = Timer.start();
 *     try {
 *         return joinPoint.proceed();
 *     } finally {
 *         sample.stop(Timer.builder("custom.operation.time")
 *                          .tag("class", joinPoint.getTarget().getClass().getSimpleName())
 *                          .tag("method", joinPoint.getSignature().getName())
 *                          .register(registry));
 *     }
 * }
 * 
 * // 4. Monitor Metrics
 * // View aspect-generated metrics:
 * GET /actuator/metrics/kafka.consumer.batch.processing
 * GET /actuator/metrics/kafka.consumer.message.processing
 * GET /actuator/metrics/kafka.consumer.api.call
 * 
 * // 5. Adding Error Tracking
 * @Around("execution(* com.example.service.*.process*(..))")
 * public Object trackErrors(ProceedingJoinPoint joinPoint) {
 *     try {
 *         return joinPoint.proceed();
 *     } catch (Exception e) {
 *         Counter.builder("errors.total")
 *                .tag("class", joinPoint.getTarget().getClass().getSimpleName())
 *                .tag("type", e.getClass().getSimpleName())
 *                .register(registry)
 *                .increment();
 *         throw e;
 *     }
 * }
 * </pre>
 * 
 * This aspect tracks:
 * - Kafka message processing times
 * - API call performance
 * - Success/failure rates
 * - DLT processing
 */
@Aspect
@Component
public class MetricsAspect {
    
    private final KafkaMetrics kafkaMetrics;
    
    public MetricsAspect(MeterRegistry registry) {
        this.kafkaMetrics = new KafkaMetrics(registry);
    }
    
    /**
     * Tracks metrics for Kafka batch message processing.
     * Intercepts @KafkaListener methods to measure:
     * - Total batch processing time
     * - Success/failure counts
     * 
     * @param joinPoint The intercepted method call
     * @return The result of the method execution
     */
    @Around("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public Object trackMessageProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start();
        try {
            Object result = joinPoint.proceed();
            sample.stop(kafkaMetrics.getBatchProcessingTimer());
            return result;
        } catch (Exception e) {
            sample.stop(kafkaMetrics.getBatchProcessingTimer());
            kafkaMetrics.incrementFailedMessages();
            throw e;
        }
    }
    
    /**
     * Tracks metrics for individual message processing.
     * Measures:
     * - Individual message processing time
     * - API failures
     * - General processing failures
     * 
     * @param joinPoint The intercepted method call
     * @return The result of the method execution
     */
    @Around("execution(* com.example.kafka.KafkaMessageConsumer.processMessage(..))")
    public Object trackSingleMessageProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        ConsumerRecord<String, ActionEvent> record = 
            (ConsumerRecord<String, ActionEvent>) joinPoint.getArgs()[0];
            
        Timer.Sample sample = Timer.start();
        try {
            Object result = joinPoint.proceed();
            sample.stop(kafkaMetrics.getMessageProcessingTimer());
            kafkaMetrics.incrementProcessedMessages();
            return result;
        } catch (ApiException e) {
            sample.stop(kafkaMetrics.getMessageProcessingTimer());
            kafkaMetrics.incrementApiFailures();
            throw e;
        } catch (Exception e) {
            sample.stop(kafkaMetrics.getMessageProcessingTimer());
            kafkaMetrics.incrementFailedMessages();
            throw e;
        }
    }
    
    /**
     * Tracks metrics for API calls.
     * Measures:
     * - API call duration
     * - Success/failure timing
     * 
     * @param joinPoint The intercepted method call
     * @return The result of the method execution
     */
    @Around("execution(* com.example.kafka.ApiService.postMessage(..))")
    public Object trackApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start();
        try {
            Object result = joinPoint.proceed();
            sample.stop(kafkaMetrics.getApiCallTimer());
            return result;
        } catch (Exception e) {
            sample.stop(kafkaMetrics.getApiCallTimer());
            throw e;
        }
    }
    
    /**
     * Tracks metrics for Dead Letter Topic (DLT) processing.
     * Measures:
     * - DLT processing time
     * - DLT message counts
     * 
     * @param joinPoint The intercepted method call
     * @return The result of the method execution
     */
    @Around("@annotation(org.springframework.kafka.annotation.DltHandler)")
    public Object trackDltProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start();
        try {
            Object result = joinPoint.proceed();
            sample.stop(kafkaMetrics.getDltProcessingTimer());
            kafkaMetrics.incrementDltMessages();
            return result;
        } catch (Exception e) {
            sample.stop(kafkaMetrics.getDltProcessingTimer());
            throw e;
        }
    }
}
