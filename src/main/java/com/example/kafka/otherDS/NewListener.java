package com.example.kafka.otherDS;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeliveryAttemptAwareRetryListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;
//import org.springframework.messaging.handler.annotation.Header;
//import org.springframework.messaging.handler.annotation.Header;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class ApiCallListener {

    private final ExternalAPIService apiService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ApiCallListener(ExternalAPIService apiService,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.apiService = apiService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "main-topic", groupId = "api-call-group")
    public void listen(ConsumerRecord<String, String> record,
                       @Header(KafkaHeaders.DELIVERY_ATTEMPT) int deliveryAttempt) {
        // Delivery attempt now available in header
        apiService.callExternalApi(record.value(), deliveryAttempt);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // Configure exponential backoff with limits
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(10_000L); // Max 10 seconds
        backOff.setMaxElapsedTime(30_000L); // Total max 30 seconds

        // Configure DLT with new naming convention (-dlt suffix)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> record.topic() + "-dlt", // New standardized suffix
                (record, ex) -> {
                    // Add custom failure metadata headers
                    record.headers().add("X-Failure-Reason",
                            ex.getMessage().getBytes(StandardCharsets.UTF_8));
                    record.headers().add("X-Delivery-Attempt",
                            String.valueOf(getDeliveryAttempt(record)).getBytes(StandardCharsets.UTF_8));
                    return record;
                });

        // Configure error handler with modern settings
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Configure exception classification
//        handler.addNotRetryableExceptions(
//                IllegalArgumentException.class,
//                ConversionException.class
//        );
//        handler.addRetryableExceptions(
//                ApiRetryableException.class,
//                TimeoutException.class
//        );

        // Add observability for retries
        handler.setRetryListeners(new DeliveryAttemptAwareRetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int attempt) {
                System.out.printf("Failed delivery attempt %d for record %s%n",
                        attempt, record);
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                System.out.println("Record recovered to DLT: " + record);
            }
        });

        return handler;
    }

    private int getDeliveryAttempt(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        return header != null ? ByteBuffer.wrap(header.value()).getInt() : 0;
    }
}